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
        <!-- ✅ 移除 v-model:content，Yjs 接管内容同步 -->
        <quill-editor ref="quillRef" :options="quillOptions" />
      </div>

      <!-- 在线用户列表（来自 Yjs Awareness） -->
      <div class="online-status">
        当前在线：{{ onlineUserList.length }} 人
        <span v-if="onlineUserList.length > 1">
          （{{
            onlineUserList
              .filter((u) => u !== currentUsername)
              .slice(0, 5)
              .join(', ')
          }}）
        </span>
      </div>

      <div class="status">
        WebSocket 状态：{{ connected ? '已连接' : '连接中...' }}
        <span v-if="wsError" style="color: red">（{{ wsError }}）</span>
      </div>

      <div class="actions">
        <button @click="saveDoc" :disabled="saving || loading">
          {{ saving ? '保存中...' : '保存' }}
        </button>
        <button @click="router.back()">返回</button>
        <button @click="exportPdf">导出为 PDF</button>
      </div>

      <p v-if="hasChanges" class="tip">有未保存的更改</p>
    </div>

    <p v-else class="error">文档加载失败</p>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'
import { Quill, QuillEditor } from '@vueup/vue-quill'
import 'quill/dist/quill.snow.css'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import QuillCursors from 'quill-cursors'

// ✅ Yjs 核心
import * as Y from 'yjs'
import { QuillBinding } from 'y-quill'
import { Awareness } from 'y-protocols/awareness'
import * as awarenessProtocol from 'y-protocols/awareness'

Quill.register('modules/cursors', QuillCursors)

// ─────────────────────────────────────────────
// 路由 & 基础状态
// ─────────────────────────────────────────────
const route = useRoute()
const router = useRouter()
const docId = route.params.id as string

const doc = ref<{ id: string; title: string } | null>(null)
const loading = ref(true)
const saving = ref(false)
const hasChanges = ref(false)
const connected = ref(false)
const wsError = ref<string | null>(null)

const currentUsername = localStorage.getItem('username') || 'unknown_user'

// ─────────────────────────────────────────────
// Quill 配置
// ─────────────────────────────────────────────
const quillRef = ref<InstanceType<typeof QuillEditor> | null>(null)

const quillOptions = {
  theme: 'snow',
  modules: {
    toolbar: [
      [{ header: [1, 2, false] }],
      ['bold', 'italic', 'underline'],
      ['link', 'image'],
      ['clean'],
    ],
    // ✅ Yjs 需要 cursor 模块显示他人光标
    cursors: {
      transformOnTextChange: true,
    },
  },
  placeholder: '开始协作编辑...',
}

// ─────────────────────────────────────────────
// Yjs 核心对象
// ─────────────────────────────────────────────
const ydoc = new Y.Doc()
const ytext = ydoc.getText('quill') // 共享文本类型，key 固定为 'quill'
const awareness = new Awareness(ydoc)

// ─────────────────────────────────────────────
// 在线用户（从 Awareness 状态派生）
// ─────────────────────────────────────────────
const onlineUserList = ref<string[]>([])

awareness.on('change', () => {
  const users: string[] = []
  awareness.getStates().forEach((state) => {
    if (state.user?.name) users.push(state.user.name)
  })
  onlineUserList.value = users
})

// 设置当前用户的 Awareness 状态（光标颜色随机）
const randomColor =
  '#' +
  Math.floor(Math.random() * 0xffffff)
    .toString(16)
    .padStart(6, '0')
awareness.setLocalStateField('user', {
  name: currentUsername,
  color: randomColor,
})

// ─────────────────────────────────────────────
// WebSocket / STOMP
// ─────────────────────────────────────────────
const stompClient = ref<Client | null>(null)
let docSubscription: any = null
let awarenessSubscription: any = null

/**
 * 将 Uint8Array 编码为 Base64 字符串（分块处理，防止栈溢出）
 */
const toBase64 = (bytes: Uint8Array): string => {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  }
  return btoa(binary)
}

/**
 * 将 Base64 字符串解码为 Uint8Array
 */
const fromBase64 = (b64: string): Uint8Array => {
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

const connectWebSocket = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    toast.error('无 token，无法实时协作')
    return
  }

  const socket = new SockJS('/ws')

  stompClient.value = new Client({
    webSocketFactory: () => socket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (str) => console.log('[STOMP]', str),
  })

  stompClient.value.onConnect = () => {
    connected.value = true
    console.log('[STOMP] 连接成功')

    // ─── 订阅：接收其他客户端的 Yjs doc update ───
    docSubscription = stompClient.value!.subscribe(`/topic/${docId}`, (message) => {
      try {
        const update = fromBase64(message.body)
        // ✅ 应用远端 update，'remote' 标记防止自身触发死循环
        Y.applyUpdate(ydoc, update, 'remote')
      } catch (e) {
        console.error('[Yjs] 应用远端 update 失败', e)
      }
    })

    // ─── 订阅：接收 Awareness（光标/在线状态）更新 ───
    awarenessSubscription = stompClient.value!.subscribe(`/topic/${docId}/awareness`, (message) => {
      try {
        const update = fromBase64(message.body)
        awarenessProtocol.applyAwarenessUpdate(awareness, update, 'remote')
      } catch (e) {
        console.error('[Awareness] 应用远端 update 失败', e)
      }
    })

    // ─── 主动广播当前用户的 Awareness 初始状态 ───
    // awareness.setLocalStateField() 在模块加载时已执行（WebSocket 未连），
    // 初始状态从未被广播，其他用户看不到新加入的用户。
    const initAwareness = awarenessProtocol.encodeAwarenessUpdate(awareness, [ydoc.clientID])
    stompClient.value!.publish({
      destination: `/app/awareness/${docId}`,
      body: toBase64(initAwareness),
    })
    console.log('[Awareness] 已广播初始状态')
  }

  stompClient.value.onStompError = (frame) => {
    wsError.value = frame.body || 'STOMP 连接错误'
    console.error('[STOMP] 错误:', frame)
  }

  stompClient.value.activate()
}

// ─────────────────────────────────────────────
// Yjs update 监听 → 发送给服务端广播
// ─────────────────────────────────────────────

/**
 * 监听本地 ydoc 变化，将 update 编码后通过 STOMP 发送
 * origin === 'remote' 时跳过，避免把别人的更新再广播一遍
 */
const onYjsUpdate = (update: Uint8Array, origin: any) => {
  if (origin === 'remote') return // 来自远端，不再转发
  if (!connected.value || !stompClient.value) return

  hasChanges.value = true

  stompClient.value.publish({
    destination: `/app/edit/${docId}`,
    body: toBase64(update), // Base64 文本帧
  })
}

/**
 * 监听 Awareness 变化，广播给其他用户（光标位置、在线状态）
 */
const onAwarenessUpdate = ({ added, updated, removed }: any) => {
  if (!connected.value || !stompClient.value) return

  const changedClients = [...added, ...updated, ...removed]
  const awarenessUpdate = awarenessProtocol.encodeAwarenessUpdate(awareness, changedClients)

  stompClient.value.publish({
    destination: `/app/awareness/${docId}`,
    body: toBase64(awarenessUpdate),
  })
}

// ─────────────────────────────────────────────
// 初始化
// ─────────────────────────────────────────────
let binding: QuillBinding | null = null

onMounted(async () => {
  // 1. 加载文档元信息 + 初始内容
  try {
    const res = await api.get(`/api/documents/${docId}`)
    doc.value = {
      id: res.data.id.toString(),
      title: res.data.title,
    }

    // 将服务端保存的初始文本插入 ytext（仅在 ytext 为空时）
    const initialText: string = res.data.content?.text || res.data.content || ''
    const yjsStateBase64: string | undefined = res.data.yjsState

    if (yjsStateBase64) {
      // 优先从 Yjs 状态快照恢复（包含完整 CRDT 历史）
      Y.applyUpdate(ydoc, fromBase64(yjsStateBase64), 'remote')
      console.log('[Yjs] 从服务端恢复 Yjs 状态，长度:', yjsStateBase64.length)
    } else if (ytext.length === 0 && initialText) {
      ydoc.transact(() => {
        ytext.insert(0, initialText)
      }, 'init') // origin 标记为 'init'，不会触发 onYjsUpdate 发送
    }
  } catch (e) {
    toast.error('加载失败')
    router.back()
    return
  } finally {
    loading.value = false
  }

  // 2. 等待 Quill 渲染完成
  await nextTick()
  await new Promise((r) => setTimeout(r, 300))

  // 3. 创建 Yjs ↔ Quill 绑定（核心！）
  const quill = quillRef.value?.getQuill()
  if (quill) {
    binding = new QuillBinding(ytext, quill, awareness)
    console.log('[Yjs] QuillBinding 创建成功')
  } else {
    console.error('[Yjs] Quill 实例未就绪')
  }

  // 4. 注册 Yjs 监听器
  ydoc.on('update', onYjsUpdate)
  awareness.on('update', onAwarenessUpdate)

  // 5. 连接 WebSocket
  connectWebSocket()
})

onBeforeUnmount(() => {
  // 清理顺序：先移除监听，再销毁绑定，最后断开连接
  ydoc.off('update', onYjsUpdate)
  awareness.off('update', onAwarenessUpdate)

  // 通知其他用户自己离线
  awareness.destroy()

  binding?.destroy()

  docSubscription?.unsubscribe()
  awarenessSubscription?.unsubscribe()
  stompClient.value?.deactivate()
})

// ─────────────────────────────────────────────
// 保存
// ─────────────────────────────────────────────
const saveDoc = async () => {
  saving.value = true
  try {
    // 从 ytext 读取纯文本作为保存内容
    const plainText = ytext.toString()

    await api.put(`/api/documents/${doc.value!.id}`, {
      title: doc.value!.title,
      content: plainText,
      yjsState: toBase64(Y.encodeStateAsUpdate(ydoc)), // 保存完整 Yjs CRDT 状态
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

// ─────────────────────────────────────────────
// 导出 PDF
// ─────────────────────────────────────────────
const exportPdf = async () => {
  try {
    const res = await api.get(`/api/documents/${docId}/export-pdf`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `${doc.value?.title ?? 'document'}.pdf`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    toast.success('PDF 导出成功')
  } catch {
    toast.error('导出失败')
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

input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  box-sizing: border-box;
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
  background: #f0f0f0;
  border: 1px solid #ddd;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.status {
  margin: 10px 0;
  font-size: 14px;
  color: #666;
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
