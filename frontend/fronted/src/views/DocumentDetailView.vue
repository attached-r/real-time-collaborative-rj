<template>
  <div class="document-detail">
    <h1>文档详情</h1>
    <p>文档 ID: {{ id }}</p>
    <button @click="router.back()">返回列表</button>
    <!-- 后期放 Quill 编辑器 -->
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const id = route.params.id as string
</script>

<style scoped>
.document-detail {
  max-width: 900px;
  margin: 40px auto;
  padding: 20px;
  background: white;
  border-radius: 8px;
}
</style>
