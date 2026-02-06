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
        <textarea v-model="doc.content" rows="15" placeholder="在这里编辑文档内容..."></textarea>
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
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'

const route = useRoute()
const router = useRouter()

const doc = ref({ id: '', title: '', content: '' })
const loading = ref(true)
const saving = ref(false)
const hasChanges = ref(false)
const original = ref({ title: '', content: '' })

onMounted(async () => {
  const id = route.params.id as string
  try {
    const res = await api.get(`/api/documents/${id}`)
    doc.value = res.data
    original.value = { title: res.data.title, content: res.data.content }
  } catch (err: any) {
    toast.error('加载失败：' + (err.response?.data?.message || '未知错误'))
    router.back()
  } finally {
    loading.value = false
  }
})

const saveDoc = async () => {
  saving.value = true
  try {
    await api.put(`/api/documents/${doc.value.id}`, {
      title: doc.value.title,
      content: doc.value.content
    })
    toast.success('保存成功')
    hasChanges.value = false
    router.back()  // 保存后直接返回列表
  } catch (err: any) {
    toast.error('保存失败：' + (err.response?.data?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 监听变化
watch(() => doc.value, (newVal) => {
  hasChanges.value = newVal.title !== original.value.title || newVal.content !== original.value.content
}, { deep: true })
</script>

<style scoped>
.edit-document {
  max-width: 900px;
  margin: 40px auto;
  padding: 30px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
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

input, textarea {
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

.loading, .error {
  text-align: center;
  padding: 100px 0;
  font-size: 18px;
  color: #777;
}

.error {
  color: #e74c3c;
}
</style>
