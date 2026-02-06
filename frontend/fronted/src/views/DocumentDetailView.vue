<template>
  <div class="document-detail">
    <h1>文档详情</h1>
    <p>文档 ID: {{ doc.id }}</p>
    <h2>标题: {{ doc.title }}</h2>
    <p>内容: {{ doc.content }}</p>
    <p>创建者: {{ doc.ownerId }}</p>
    <button @click="router.back()">返回列表</button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'

interface Document {
  id: string
  title: string
  content: string
  ownerId: string
}

const route = useRoute()
const router = useRouter()
const doc = ref<Document>({ id: '', title: '', content: '', ownerId: '' })
const loading = ref(true)

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await api.get(`/api/documents/${id}`)
    doc.value = res.data
  } catch (err: any) {
    toast.error('加载文档失败: ' + (err.response?.data || '未知错误'))
    if (err.response?.status === 403) {
      toast.error('无权限查看此文档')
      router.push('/documents')
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.document-detail {
  max-width: 800px;
  margin: 40px auto;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

h1 {
  margin-bottom: 20px;
}

p {
  margin-bottom: 15px;
  font-size: 16px;
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
</style>
