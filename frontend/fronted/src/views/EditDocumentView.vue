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
        <!-- 【改动点】：移除 v-model:content，手动同步 -->
        <quill-editor
          ref="quillRef"
          :options="quillOptions"
          @text-change="onTextChange"
          contentType="text"
          v-model:content="doc.content"
        />
      </div>

      <div class="status">
        WebSocket 状态：{{ connected ? '已连接' : '连接中...' }}
        <span v-if="error" style="color: red">（{{ error }}）</span>
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

const route = useRoute()
const router = useRouter()
const quillRef = ref<InstanceType<typeof QuillEditor> | null>(null)

const doc = ref({ id: '', title: '', content: '' })
const loading = ref(true)
const saving = ref(false)
const hasChanges = ref(false)

const currentUsername = localStorage.getItem('username') || 'unknown_user'

// WebSocket 相关
const client = ref<Client | null>(null)
const connected = ref(false)
const error = ref<string | null>(null)
let subscription: any = null

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

// 连接 WebSocket
const connectWebSocket = () => {
  const token = localStorage.getItem('token')
  if (!token) return

  const docId = route.params.id as string
  const socket = new SockJS('http://localhost:8080/ws')

  client.value = new Client({
    webSocketFactory: () => socket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    debug: (str) => console.log('[STOMP]', str),
    reconnectDelay: 5000,
  })

  client.value.onConnect = () => {
    connected.value = true
    console.log('[STOMP] 连接成功')
    if (client.value)
      subscription = client.value.subscribe(`/topic/${docId}`, (message) => {
        console.log('[STOMP] 收到广播原始数据:', message.body)
        let newContent = message.body // 默认用原始字符串（降级方案）

        try {
          const body = JSON.parse(message.body) // 解析后端返回的 JSON
          newContent = body.content || message.body // 提取 content 字段（字符串）
          const sender = body.sender || 'unknown'

          console.log(`[STOMP] 收到 ${sender} 的更新内容:`, newContent.substring(0, 50) + '...')

          // 如果是自己发送的，不更新（避免光标跳动）
          if (sender !== currentUsername) {
            setQuillContent(newContent)
          }
        } catch (e) {
          console.error('解析广播消息失败:', e, '原始数据:', message.body)
          // 降级处理：如果解析失败，直接用原始字符串
          setQuillContent(message.body)
        }
      })
  }

  client.value.activate()
}

// 设置 Quill 内容
const setQuillContent = (text: string) => {
  const quill = quillRef.value?.getQuill()
  if (quill && text !== undefined && text !== null) {
    if (quill.getText().trim() !== text.trim()) {
      quill.setText(text)
      console.log('[前端] setQuillContent 更新成功:', text.substring(0, 50) + '...')
    }
  } else {
    console.warn('[前端] setQuillContent 失败，Quill 未就绪或 text 为空')
  }
}

// Quill 变化发送完整文本
const onTextChange = (delta: { ops: any[] }, oldDelta: any, source: string) => {
  console.log('onTextChange事件已触发！source:', source)

  if (source !== 'user') return
  hasChanges.value = true

  if (!connected.value || !client.value) {
    console.log('[前端] WebSocket 未就绪，跳过发送')
    return
  }

  const quill = quillRef.value?.getQuill()
  if (!quill) {
    console.log('[前端] Quill 实例未找到')
    return
  }

  const fullText = quill.getText() // 不 trim，保留所有字符
  console.log('[前端] 获取完整文本:', fullText.substring(0, 50) + '...', '长度:', fullText.length)

  const docId = route.params.id as string
  client.value.publish({
    destination: `/app/edit/${docId}`,
    body: fullText,
  })
  console.log('[前端] 发送完整文本到:', `/app/edit/${docId}`, '长度:', fullText.length)
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

    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 100))

    const quill = quillRef.value?.getQuill()
    if (quill) {
      quill.setText(doc.value.content)
      console.log('[前端] 强制设置 Quill 内容成功:', doc.value.content.substring(0, 50) + '...')
    } else {
      console.warn('[前端] Quill 实例仍未就绪')
    }
  } catch (err) {
    toast.error('加载失败')
    router.back()
  } finally {
    loading.value = false
  }

  connectWebSocket()
})

onBeforeUnmount(() => {
  subscription?.unsubscribe()
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

.status {
  margin: 10px 0;
  font-size: 14px;
  color: #666;
}
</style>
