<template>
  <div class="document-list">
    <div class="header">
      <h1>我的文档</h1>
      <button @click="createDoc" :disabled="loading">新建文档</button>
      <button @click="logout" class="logout-btn">登出</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <ul v-else-if="documents.length > 0">
      <li v-for="doc in documents" :key="doc.id" class="doc-item">
        <div class="doc-info">
          <h3>{{ doc.title }}</h3>
          <p class="id">ID: {{ doc.id }}</p>
        </div>
        <button @click="viewDoc(doc.id)">查看</button>
      </li>
    </ul>

    <p v-else class="empty">暂无文档，快去新建一个吧！</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify' // 引入 toast

interface Document {
  id: string
  title: string
  content?: string
  ownerId?: string
}

const documents = ref<Document[]>([])
const loading = ref(false)
const router = useRouter()

onMounted(async () => {
  await fetchDocuments()
})

const fetchDocuments = async () => {
  loading.value = true
  try {
    const res = await api.get('/api/documents')
    documents.value = res.data
  } catch (err: any) {
    console.error('获取文档失败', err)
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('token')
      router.push('/login')
    }
  } finally {
    loading.value = false
  }
}

const createDoc = async () => {
  loading.value = true
  try {
    const res = await api.post('/api/documents', {
      title: `新文档 ${new Date().toLocaleString()}`,
      content: '这是新文档的初始内容...',
    })
    alert('创建成功！ID: ' + res.data.id)
    await fetchDocuments() // 刷新列表
  } catch (err: any) {
    alert('创建失败：' + (err.response?.data?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const viewDoc = (id: string) => {
  router.push(`/documents/${id}`)
  // 后期跳转到编辑页
}

const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped>
.document-list {
  max-width: 900px;
  margin: 40px auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

h1 {
  margin: 0;
  color: #333;
}

button {
  padding: 10px 20px;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:hover {
  background: #357abd;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

ul {
  list-style: none;
  padding: 0;
}

.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  margin-bottom: 10px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.doc-info h3 {
  margin: 0 0 5px;
  font-size: 18px;
}

.id {
  margin: 0;
  color: #777;
  font-size: 14px;
}

.loading,
.empty {
  text-align: center;
  color: #777;
  font-size: 18px;
  padding: 50px 0;
}
.actions {
  display: flex;
  gap: 12px;
}

.logout-btn {
  background: #e74c3c;
  color: white;
}

.logout-btn:hover {
  background: #c0392b;
}

.loading {
  text-align: center;
  font-size: 18px;
  color: #666;
  padding: 50px 0;
}
</style>
