<template>
  <div class="document-list">
    <div class="header">
      <h1>我的文档</h1>

      <!-- 搜索框 -->
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文档标题…"
          clearable
          @keyup.enter="searchDocs"
          style="width: 240px; margin-right: 10px"
        />
        <el-button type="primary" @click="searchDocs" :loading="searchLoading"> 搜索 </el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <button @click="createDoc" :disabled="loading">
        {{ loading ? '创建中...' : '新建文档' }}
      </button>
      <button @click="logout" class="logout-btn">登出</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <ul v-else-if="documents.length > 0">
      <li v-for="doc in documents" :key="doc.id" class="doc-item">
        <div class="doc-info">
          <h3>{{ doc.title }}</h3>
          <p class="id">ID: {{ doc.id }}</p>
        </div>
        <div class="actions">
          <button @click="viewDoc(doc.id)">查看</button>
          <button @click="editDoc(doc.id)" class="edit-btn">编辑</button>
          <button @click="shareDoc(doc.id)" class="share-btn">分享</button>
          <button @click="deleteDoc(doc.id)" class="delete-btn">删除</button>
        </div>
      </li>
    </ul>

    <p v-else class="empty">暂无文档，快去新建一个吧！</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'
import { ElMessage } from 'element-plus'

interface Document {
  id: string
  title: string
  content?: string
  ownerId?: string
}

interface ApiError {
  response?: {
    status?: number
    data?: { message?: string } | string
  }
  message?: string
}

const documents = ref<Document[]>([])
const loading = ref(false)
const searchLoading = ref(false)
const searchKeyword = ref('')
const router = useRouter()

onMounted(async () => {
  await fetchDocuments()
})

// 修改：支持搜索我的文档 + 被分享的文档
const fetchDocuments = async (keyword = '') => {
  loading.value = true
  try {
    const token = localStorage.getItem('token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    let res
    if (keyword.trim()) {
      // 有关键词 → 只搜我的文档（保持原逻辑）
      const params = { keyword: keyword.trim() }
      res = await api.get('/api/documents/search', { params })
    } else {
      // 无关键词 → 获取所有我能访问的文档（我的 + 被分享的）
      res = await api.get('/api/documents/my-accessible')
    }
    documents.value = res.data
  } catch (err: any) {
    const error = err as ApiError
    console.error('获取文档失败', error)
    const errorMessage =
      typeof error.response?.data === 'object' && error.response.data !== null
        ? error.response.data.message
        : error.response?.data
    toast.error('获取文档失败：' + (errorMessage || '未知错误'))
    // if (error.response?.status === 401 || error.response?.status === 403) {
    //   localStorage.removeItem('token')
    //   router.push('/login')
    // }
  } finally {
    loading.value = false
  }
}

// 搜索
const searchDocs = async () => {
  searchLoading.value = true
  await fetchDocuments(searchKeyword.value)
  searchLoading.value = false
  if (documents.value.length === 0) {
    ElMessage.info('未找到匹配的文档')
  }
}

// 重置搜索
const resetSearch = () => {
  searchKeyword.value = ''
  fetchDocuments()
}

// 创建文档
const createDoc = async () => {
  loading.value = true
  try {
    const res = await api.post('/api/documents', {
      title: `新文档 ${new Date().toLocaleString()}`,
      content: '这是新文档的初始内容...',
    })
    toast.success('创建成功！ID: ' + res.data.id)
    await fetchDocuments(searchKeyword.value)
  } catch (err: any) {
    const error = err as ApiError
    const errorMessage =
      typeof error.response?.data === 'object' && error.response.data !== null
        ? error.response.data.message
        : error.response?.data
    toast.error('创建失败：' + (errorMessage || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 查看文档详情
const viewDoc = (id: string) => {
  router.push(`/documents/${id}`)
}

// 编辑文档
const editDoc = (id: string) => {
  router.push(`/edit/${id}`)
}

// 新增：分享文档
const shareDoc = async (id: string) => {
  const username = prompt('请输入要分享的用户名：')
  if (!username || username.trim() === '') {
    return toast.warning('用户名不能为空')
  }

  try {
    await api.post(`/api/documents/${id}/share`, {
      username: username.trim()
    })
    toast.success(`已成功分享给 ${username}`)
  } catch (err: any) {
    const error = err as ApiError
        toast.error('分享失败：' + (typeof error.response?.data === 'string' ? error.response?.data : error.response?.data?.message || '未知错误'))
  }
}

// 删除文档
const deleteDoc = async (id: string) => {
  if (!confirm('确定要删除这个文档吗？此操作不可恢复！')) return

  try {
    await api.delete(`/api/documents/${id}`)
    toast.success('删除成功')
    await fetchDocuments(searchKeyword.value)
  } catch (err: any) {
    const error = err as ApiError
    toast.error('删除失败：' + (error.response?.data || '未知错误'))
    if (error.response?.status === 403) {
      toast.error('无权限删除此文档')
    }
  }
}

// 登出
const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped>
/* 原有样式保持不变，只新增分享按钮样式 */
.share-btn {
  background: #28a745;  /* 绿色，代表分享 */
  color: white;
}

.share-btn:hover {
  background: #218838;
}
/* 其余样式完全不变 */

.document-list {
  max-width: 960px;
  margin: 60px auto;
  padding: 0 20px;
  background: var(--bg--color);
  color: var(--test-color);
}

.header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 40px;
}

h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 320px;
}

.search-box :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.actions {
  display: flex;
  gap: 12px;
}

button,
.logout-btn,
.delete-btn,
.edit-btn {
  padding: 10px 20px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s ease;
  cursor: pointer;
}

button {
  background:var(--primary);
  color: white;
  border: none;
}

button:hover:not(:disabled) {
  background: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59,130,246,0.3);
}

.logout-btn {
  background: var(--danger);
}

.logout-btn:hover {
  background: #dc2626;
}

.edit-btn {
  background: #f59e0b;
}

.edit-btn:hover {
  background: #d97706;
}

.delete-btn {
  background: #dc2626;
}

.delete-btn:hover {
  background: #b91c1c;
}

.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  margin-bottom: 16px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transition: all 0.2s ease;
}

.doc-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}

.doc-info h3 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #111827;
}

.id {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.loading, .empty {
  text-align: center;
  color: #6b7280;
  font-size: 18px;
  padding: 80px 0;
}


</style>
