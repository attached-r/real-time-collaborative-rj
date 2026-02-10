<template>
  <div class="document-detail">
    <h1>文档详情</h1>
    <p>文档 ID: {{ doc.id }}</p>
    <h2>标题: {{ doc.title }}</h2>
    <!-- 【改动点】：content 是 Document，提取 text -->
    <p>内容: {{ typeof doc.content === 'object' && doc.content !== null ? doc.content.text : doc.content }}</p>
    <p>创建者: {{ doc.ownerId }}</p>
    <button @click="router.back()">返回列表</button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'

/** 【改动点】更新接口类型，适配后端 BSON Document */
interface Document {
  id: string
  title: string
  content?: { text: string } | string   // 支持 Document 或字符串
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

    // 【改动点】数据转换：提取 content.text
    doc.value = {
      id: res.data.id.toString(),
      title: res.data.title,
      content: res.data.content,   // 保留原始结构，后续可提取 .text
      ownerId: res.data.ownerId
    }
  } catch (err: any) {
    toast.error('加载文档失败: ' + (err.response?.data?.message || '未知错误'))
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
