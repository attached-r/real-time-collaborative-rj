<template>
  <div class="edit-document">
    <h1>编辑文档</h1>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="doc">
      <div class="form-group">
        <label>标题</label>
        <input v-model="doc.title" type="text" placeholder="文档标题" />
      </div>

      <div class="form-group">
        <label>内容</label>
        <!-- v-model + @input 配合使用，确保实时响应 -->
        <textarea
          v-model="doc.content"
          rows="15"
          placeholder="在这里编辑文档内容..."
          @input="onContentChange"
        ></textarea>
      </div>

      <!-- 实时协作状态显示 -->
      <div class="status" style="margin: 10px 0; color: #666; font-size: 14px">
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
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const route = useRoute()
const router = useRouter()

// 文档数据
const doc = ref({ id: '', title: '', content: '' })
const original = ref({ title: '', content: '' }) // 用于判断是否有变化（可选）

const loading = ref(true)
const saving = ref(false)
const hasChanges = ref(false)

// WebSocket 相关
const client = ref<Client | null>(null)
const connected = ref(false)
const error = ref<string | null>(null)
let subscription: any = null

// 关键：记录上一次成功发送的完整内容，用于计算增量
const lastSentContent = ref('')

// 连接 WebSocket 并订阅当前文档频道
const connectWebSocket = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    toast.error('无登录 token，无法开启实时协作')
    return
  }

  const docId = route.params.id as string
  const socket = new SockJS('http://localhost:8080/ws')

  client.value = new Client({
    webSocketFactory: () => socket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    debug: (str) => console.log('[STOMP]', str),
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })

  client.value.onConnect = () => {
    connected.value = true
    error.value = null
    console.log('[STOMP] 已连接')

    // 订阅广播频道：后端广播到 /topic/{docId}
    if (client.value)
      subscription = client.value.subscribe(`/topic/${docId}`, (message) => {
        console.log('[STOMP] 收到广播:', message.body)

        const received = message.body
        if (typeof received === 'string') {
          // 直接覆盖为后端返回的完整内容
          doc.value.content = received
          // 同步 lastSentContent，避免下次误算增量
          lastSentContent.value = received
          toast.info('收到其他人的实时编辑')
        } else {
          console.error('收到非字符串广播:', received)
        }
      })
  }

  client.value.onStompError = (frame) => {
    error.value = frame.body || 'STOMP 连接错误'
    console.error('[STOMP 错误]', frame)
    toast.error('实时连接失败')
  }

  client.value.onWebSocketClose = () => {
    connected.value = false
    console.log('[STOMP] 连接断开')
  }

  client.value.activate()
}

// 组件挂载时加载文档 + 连接 WebSocket
onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await api.get(`/api/documents/${id}`)
    doc.value = res.data
    original.value = { title: res.data.title, content: res.data.content }
    lastSentContent.value = res.data.content || '' // 初始化上次发送内容
  } catch (err: any) {
    toast.error('加载失败：' + (err.response?.data?.message || '未知错误'))
    router.back()
    return
  } finally {
    loading.value = false
  }

  connectWebSocket()
})

// 组件卸载时清理订阅和连接
onBeforeUnmount(() => {
  if (subscription) subscription.unsubscribe()
  client.value?.deactivate()
})

// 只发送增量（delta）
const sendContent = () => {
  if (!connected.value || !client.value) return

  const current = doc.value.content || ''
  const last = lastSentContent.value

  // 计算真正的增量
  let delta = ''
  if (current.startsWith(last)) {
    delta = current.slice(last.length) // 只取新增部分
  } else {
    // 如果内容被删除或大幅修改，保守发送完整内容
    delta = current
  }

  if (!delta.trim()) return // 空变化不发

  const docId = route.params.id as string
  client.value.publish({
    destination: `/app/edit/${docId}`,
    body: delta, // 直接发送增量字符串（后端会追加）
  })

  console.log('[前端] 发送增量:', delta.substring(0, 50) + (delta.length > 50 ? '...' : ''))

  // 更新记录
  lastSentContent.value = current
}

// 监听内容变化 + 防抖发送（300ms）
let debounceTimer: number | null = null
watch(
  () => doc.value.content,
  () => {
    hasChanges.value = true

    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      sendContent()
    }, 300)
  },
  { deep: true },
)

// @input 触发时也可以调用，但 watch 已覆盖
const onContentChange = () => {
  // 可选：这里可以加一些本地即时效果（如字数统计）
}

// 手动保存到数据库
const saveDoc = async () => {
  saving.value = true
  try {
    await api.put(`/api/documents/${doc.value.id}`, {
      title: doc.value.title,
      content: doc.value.content,
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
