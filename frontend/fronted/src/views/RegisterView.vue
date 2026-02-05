<template>
  <div class="register-container">
    <h2>注册实时协作文档系统</h2>

    <form method="post" @submit.prevent="handleRegister">
      <div class="form-group">
        <label for="username">用户名</label>
        <input
          id="username"
          v-model="form.username"
          type="text"
          placeholder="请输入用户名（3-20位字母数字）"
          required
          autocomplete="username"
        />
      </div>

      <div class="form-group">
        <label for="email">邮箱</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          placeholder="请输入邮箱地址"
          required
          autocomplete="email"
        />
      </div>

      <div class="form-group">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          placeholder="请输入密码（至少6位）"
          required
          autocomplete="new-password"
        />
      </div>

      <div class="form-group">
        <label for="confirmPassword">确认密码</label>
        <input
          id="confirmPassword"
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入密码"
          required
          autocomplete="new-password"
        />
      </div>

      <button type="submit" :disabled="loading" class="submit-btn">
        {{ loading ? '注册中...' : '立即注册' }}
      </button>
    </form>

    <p v-if="error" class="error-message">{{ error }}</p>

    <div class="extra">
      <p>已有账号？<router-link to="/login">立即登录</router-link></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { toast } from 'vue3-toastify'

const router = useRouter()

const form = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loading = ref(false)
const error = ref('')

const handleRegister = async () => {
  // 前端简单校验
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致'
    toast.error('两次输入的密码不一致')
    return
  }

  if (form.value.password.length < 6) {
    error.value = '密码长度至少6位'
    toast.error('密码长度至少6位')
    return
  }

  loading.value = true
  error.value = ''

  try {
    await api.post('/api/auth/register', {
      username: form.value.username.trim(),
      email: form.value.email.trim(),
      password: form.value.password.trim()
    })

    toast.success('注册成功！请登录', {
      position: 'top-center',
      autoClose: 3000
    })

    // 清空表单
    form.value = { username: '', email: '', password: '', confirmPassword: '' }

    // 跳转登录页
    router.push('/login')
  } catch (err: any) {
    const msg = err.response?.data?.message || '注册失败，请稍后再试'
    error.value = msg
    toast.error(msg, {
      position: 'top-center',
      autoClose: 5000
    })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  max-width: 420px;
  margin: 80px auto;
  padding: 40px 32px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  text-align: center;
}

h2 {
  margin-bottom: 32px;
  font-size: 24px;
  color: #1a1a1a;
}

.form-group {
  margin-bottom: 24px;
  text-align: left;
}

label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 15px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #4a90e2;
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.1);
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #357abd;
}

.submit-btn:disabled {
  background: #a0c4ff;
  cursor: not-allowed;
}

.error-message {
  margin-top: 16px;
  color: #e74c3c;
  font-size: 14px;
}

.extra {
  margin-top: 24px;
  font-size: 14px;
  color: #666;
}

.extra a {
  color: #4a90e2;
  text-decoration: none;
  font-weight: 500;
}

.extra a:hover {
  text-decoration: underline;
}
</style>
