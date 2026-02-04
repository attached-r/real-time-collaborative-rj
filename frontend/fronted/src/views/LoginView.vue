<template>
  <div class="login-container">
    <h2>登录实时协作文档系统</h2>
    <form method="post" @submit.prevent="handleLogin">
      <div class="form-group">
        <label>用户名</label>
        <input v-model="username" type="text" placeholder="请输入用户名" required />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" required />
      </div>
      <button type="submit" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="extra">
      <p>还没有账号？ <router-link to="/register">注册</router-link></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const router = useRouter()

const handleLogin = async () => {
  loading.value = true
  error.value = ''

  try {
    const res = await api.post('/api/auth/login', {
      username: username.value.trim(),
      password: password.value.trim()
    })

    const token = res.data.token
    if (!token) {
      throw new Error('登录响应中没有 token')
    }

    localStorage.setItem('token', token)
    router.push('/documents')
  } catch (err: any) {
    console.error('登录失败', err)
    error.value = err.response?.data?.message || '登录失败，请检查用户名或密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  max-width: 400px;
  margin: 120px auto;
  padding: 30px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
  text-align: center;
}

h2 {
  margin-bottom: 30px;
  color: #333;
}

.form-group {
  margin-bottom: 20px;
  text-align: left;
}

label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
  color: #555;
}

input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
  box-sizing: border-box;
}

button {
  width: 100%;
  padding: 14px;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.3s;
}

button:hover {
  background: #357abd;
}

button:disabled {
  background: #aaa;
  cursor: not-allowed;
}

.error {
  color: #e74c3c;
  margin-top: 15px;
}

.extra {
  margin-top: 20px;
  font-size: 14px;
  color: #777;
}

.extra a {
  color: #4a90e2;
  text-decoration: none;
}
</style>
