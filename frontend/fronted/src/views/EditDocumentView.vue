<template>
  <div class="edit-document">
    <h1>编辑文档 - 实时协作</h1>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="doc">
      <div class="form-group">
        <label>标题</label>
        <input v-model="doc.title" type="text" placeholder="文档标题" />
      </div>

      <div class="form-group">
        <label>内容（实时协作）</label>
        <quill-editor
          ref="quillRef"
          :options="quillOptions"
          contentType="text"
          v-model:content="doc.content"
        />
      </div>

      <!-- 【新增】在线人数显示 -->
      <div class="online-status">
        当前在线：{{ onlineUsers.length }} 人
        <span v-if="onlineUsers.length > 1">
          （{{ onlineUsers.filter(u => u !== currentUsername).slice(0, 5).join(', ') }}）
        </span>
      </div>

      <div class="status">
        WebSocket 状态：{{ connected ? '已连接' : '连接中...' }}
        <span v-if="error" style="color: red;">（{{ error }}）</span>
      </div>

      <div class="actions">
        <button @click="saveDoc" :disabled="saving || loading">
          {{ saving ? '保存中...' : '保存' }}
        </button>
        <button @click="router.back()">返回</button>
      </div>

      <p v-if="hasChanges" class="tip">有未保存的更改</p>
    </div>

    <p v-else class="error">文档加载失败</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'
import { QuillEditor } from '@vueup/vue-quill'
import 'quill/dist/quill.snow.css'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import debounce from 'lodash/debounce'
import { diff_match_patch } from 'diff-match-patch'
const isApplyingRemote = ref(false)
const route = useRoute()
const router = useRouter()
const quillRef = ref<InstanceType<typeof QuillEditor> | null>(null)

const doc = ref({ id: '', title: '', content: '' })
const loading = ref(true)
const saving = ref(false)
const hasChanges = ref(false)

const dmp = new diff_match_patch()

// 【新增】在线用户列表
const onlineUsers = ref<string[]>([])
const currentUsername = localStorage.getItem('username') || 'unknown_user'

// WebSocket 相关
const client = ref<Client | null>(null)
const connected = ref(false)
const error = ref<string | null>(null)
let subscription: any = null
let onlineSubscription: any = null  // 新增：用于在线列表订阅

const quillOptions = {
  theme: 'snow',
  modules: {
    toolbar: [
      [{ header: [1, 2, false] }],
      ['bold', 'italic', 'underline'],
      ['link', 'image'],
      ['clean'],
    ],
  },
  placeholder: '开始协作编辑...',
}
// 防抖函数
const debouncedSend = debounce((text: string) => {
  if (!connected.value || !client.value) return

  const docId = route.params.id as string
  client.value.publish({
    destination: `/app/edit/${docId}`,
    body: text,
  })
  console.log('[防抖发送] 长度:', text.length)
}, 450)

// 连接 WebSocket
const connectWebSocket = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    toast.error('无 token，无法实时协作')
    return
  }

  const docId = route.params.id as string
  const socket = new SockJS('http://localhost:8080/ws')

  client.value = new Client({
    webSocketFactory: () => socket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    debug: (str) => console.log('[STOMP]', str),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  })

  client.value.onConnect = () => {
    connected.value = true
    console.log('[STOMP] 连接成功')

    // 订阅文档内容广播（你的原有逻辑）
    if(client.value)
    subscription = client.value.subscribe(`/topic/${docId}`, (message) => {
      console.log('[收到广播] 原始 body:', message.body)

      let data
      try {
        data = JSON.parse(message.body)
        console.log('[解析成功] sender:', data.sender, 'content length:', data.content?.length || 0)
      } catch (e) {
        console.error('[解析失败] 使用原始 body', e)
        data = { content: message.body, sender: 'unknown' }
      }

      const newContent = data.content || ''
      const sender = data.sender || 'unknown'

      if (sender === currentUsername) {
        console.log('[忽略自己发的广播] sender 匹配当前用户')
        return
      }

      console.log(`[将要应用远程更新] 来自 ${sender}，内容长度 ${newContent.length}`)

      setQuillContent(newContent)  // 调用上面改过的函数
    })

    // 【新增】订阅在线用户列表
    if(client.value)
    onlineSubscription = client.value.subscribe(`/topic/${docId}/online`, (message) => {
      try {
        const users = JSON.parse(message.body)  // 后端广播的是 Set 的 JSON 数组，如 ["user1","user2"]
        onlineUsers.value = Array.from(users)
        console.log('[前端] 更新在线用户列表:', onlineUsers.value)
      } catch (e) {
        console.error('解析在线用户列表失败:', e, '原始数据:', message.body)
        onlineUsers.value = []
      }
    })
    // 【关键修复】订阅成功后，立即手动请求一次当前在线状态
    setTimeout(() => {
      if(client.value)
      client.value.publish({
        destination: `/app/online/${docId}`  // 后端端点
      })
      console.log('[前端] 订阅后主动请求在线用户列表')
  }, 500)  // 延迟 500ms，确保订阅通道已就绪
}


  client.value.onStompError = (frame) => {
    error.value = frame.body || 'STOMP 连接错误'
    console.error('[STOMP] 错误:', frame)
  }

  client.value.activate()
}
// 设置 Quill 内容
const setQuillContent = (text: string) => {
  const quill = quillRef.value?.getQuill()
  if (!quill) return

  const currentText = quill.getText()

  // 放宽判断：只要内容长度或内容有差异就更新（避免 trim 误判空格/换行）
  if (currentText !== text) {   // 改成 !== 而非 trim 比较
    quill.setText(text, 'silent')   // 必须加 'silent'！！
    console.log('[setQuillContent] 更新成功 (silent):', text.substring(0, 50))
  } else {
    console.log('[setQuillContent] 内容相同，跳过更新')
  }
}

// Quill 变化发送完整文本
const onTextChange = (delta: any, oldDelta: any, source: string) => {
  if (source !== 'user' || isApplyingRemote.value){
    console.log('非用户来源，跳过发送')
    return
  }

  hasChanges.value = true

  const quill = quillRef.value?.getQuill()
  if (!quill) return

  const fullText = quill.getText()  // 保留 \n

  debouncedSend(fullText)
}

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await api.get(`/api/documents/${id}`)
    doc.value = {
      id: res.data.id.toString(),
      title: res.data.title,
      content: res.data.content?.text || res.data.content || '',
    }

    // 等待 Quill 组件真正渲染完成（关键修复）
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 500))  // 先试 500ms，如果还不行改成 800 或 1000

    const quill = quillRef.value?.getQuill()
    if (quill) {
      quill.setText(doc.value.content, 'silent')
      console.log('[前端] Quill 实例就绪，初始内容设置成功:', doc.value.content.substring(0, 50) + '...')
    } else {
      console.error('[前端] Quill 实例仍未就绪，等待时间可能不足')
      // 可选：再等一次（极端情况）
      setTimeout(() => {
        const retryQuill = quillRef.value?.getQuill()
        if (retryQuill) {
          retryQuill.setText(doc.value.content, 'silent')
          console.log('[重试成功] Quill 初始内容设置完成')
        }
      }, 800)
    }
  } catch (err) {
    toast.error('加载失败')
    router.back()
  } finally {
    loading.value = false   // 无论成功失败都关闭 loading
  }

  connectWebSocket()
})

onBeforeUnmount(() => {
  subscription?.unsubscribe()
  onlineSubscription?.unsubscribe()  // 【新增】取消在线列表订阅
  client.value?.deactivate()
})

// 保存
const saveDoc = async () => {
  saving.value = true
  try {
    const quill = quillRef.value?.getQuill()
    const plainText = quill ? quill.getText().trim() : doc.value.content

    await api.put(`/api/documents/${doc.value.id}`, {
      title: doc.value.title,
      content: plainText,
    })

    toast.success('保存成功')
    hasChanges.value = false
    router.back()
  } catch (err: any) {
    toast.error('保存失败：' + (err.response?.data?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.status {
  margin: 10px 0;
  font-size: 14px;
  color: #666;
}
.edit-document {
  max-width: 900px;
  margin: 40px auto;
  padding: 30px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

h1 {
  margin-bottom: 30px;
  text-align: center;
}

.form-group {
  margin-bottom: 24px;
}

label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
}

input,
textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  box-sizing: border-box;
}

textarea {
  min-height: 400px;
  resize: vertical;
}

.actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 30px;
}

button {
  padding: 12px 28px;
  font-size: 16px;
  border-radius: 6px;
  cursor: pointer;
}

button.primary {
  background: #4a90e2;
  color: white;
  border: none;
}

button.primary:hover {
  background: #357abd;
}

button.secondary {
  background: #f0f0f0;
  border: 1px solid #ddd;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tip {
  text-align: center;
  color: #e74c3c;
  margin-top: 16px;
}

.loading,
.error {
  text-align: center;
  padding: 100px 0;
  font-size: 18px;
  color: #777;
}

.error {
  color: #e74c3c;
}

.online-status {
  margin: 15px 0;
  padding: 10px;
  background: #f0f9ff;
  border-radius: 8px;
  text-align: center;
  font-size: 15px;
  color: #1e40af;
  font-weight: 500;
}
</style>
