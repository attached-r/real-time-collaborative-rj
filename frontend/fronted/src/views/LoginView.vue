<template>
  <div class="login-page">
    <div class="background-glow"></div>

    <div class="login-card">
      <div class="logo-wrapper">
        <div class="logo-glow"></div>
        <h1>协作文档</h1>
        <p class="tagline">多人编辑，灵感永不丢失</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="input-wrapper">
          <input
            v-model="username"
            id="username"
            type="text"
            placeholder=" "
            required
            autocomplete="username"
          />
          <label for="username">用户名</label>
          <div class="input-glow"></div>
        </div>

        <div class="input-wrapper">
          <input
            v-model="password"
            id="password"
            type="password"
            placeholder=" "
            required
            autocomplete="current-password"
          />
          <label for="password">密码</label>
          <div class="input-glow"></div>
        </div>

        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading" class="loading-dot">登录中</span>
          <span v-else>进入协作空间</span>
        </button>
      </form>

      <p v-if="error" class="error-msg">{{ error }}</p>

      <div class="footer-links">
        <router-link to="/register" class="link">创建新账号</router-link>
      </div>
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
      password: password.value.trim(),
    })

    const token = res.data.token
    if (!token) throw new Error('无 token')

    localStorage.setItem('token', token)
    router.push('/documents')
  } catch (err: any) {
    error.value = err.response?.data?.message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  position: relative;
  overflow: hidden;
  padding: 20px; /* 手机端留边距 */
  box-sizing: border-box;
}

.background-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 30%, rgba(99, 102, 241, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(139, 92, 246, 0.12) 0%, transparent 50%);
  pointer-events: none;
  z-index: 0;
}

.login-card {
  position: relative;
  background: rgba(30, 41, 59, 0.65);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border-radius: 24px;
  padding: 48px 40px;
  width: 100%;
  max-width: 460px; /* 限制最大宽度 */
  margin: 0 auto; /* 强制水平居中 */
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow:
    0 30px 80px rgba(0, 0, 0, 0.5),
    inset 0 0 40px rgba(255, 255, 255, 0.03);
  overflow: hidden;
  z-index: 1;
}

.logo-wrapper {
  text-align: center;
  margin-bottom: 40px;
  position: relative;
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.3) 0%, transparent 70%);
  transform: translate(-50%, -50%);
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.6;
  z-index: -1;
}

h1 {
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(90deg, #c084fc, #a78bfa, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

.tagline {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 15px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.input-wrapper {
  position: relative;
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中（如果有高度） */
}

input {
  width: 100%;
  padding: 16px 20px 16px 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  font-size: 16px;
  color: white;
  transition: all 0.3s ease;
  outline: none;
}

input:focus {
  border-color: #a78bfa;
  box-shadow: 0 0 0 4px rgba(167, 139, 250, 0.15);
  background: rgba(255, 255, 255, 0.08);
}

label {
  position: absolute;
  left: 20px;
  top: 16px;
  color: #94a3b8;
  font-size: 16px;
  pointer-events: none;
  transition: all 0.3s ease;
  transform-origin: left;
}

input:focus + label,
input:not(:placeholder-shown) + label {
  top: -10px;
  left: 16px;
  font-size: 12px;
  color: #a78bfa;
  background: rgba(15, 23, 42, 0.9);
  padding: 0 8px;
  border-radius: 4px;
}

.submit-btn {
  margin-top: 12px;
  padding: 16px;
  background: linear-gradient(90deg, #7c3aed, #4f46e5, #6366f1);
  background-size: 200% 100%;
  background-position: 0% 0%;
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.4s ease;
  position: relative;
  overflow: hidden;
}

.submit-btn:hover {
  background-position: 100% 0%;
  box-shadow: 0 12px 32px rgba(124, 58, 237, 0.4);
  transform: translateY(-2px);
}

.submit-btn:disabled {
  background: #475569;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.loading-dot {
  display: inline-block;
  animation: dots 1.4s infinite;
}

@keyframes dots {
  0%,
  20% {
    content: '.';
  }
  40% {
    content: '..';
  }
  60% {
    content: '...';
  }
  80%,
  100% {
    content: '';
  }
}

.error-msg {
  color: #f87171;
  margin: 16px 0 0;
  font-size: 14px;
  text-align: center;
  animation: fadeIn 0.4s ease;
}

.footer-links {
  margin-top: 32px;
  display: flex;
  justify-content: center;
  gap: 32px;
  font-size: 14px;
  color: #94a3b8;
}

.link {
  color: #a78bfa;
  text-decoration: none;
  transition: color 0.2s;
}

.link:hover {
  color: #c084fc;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 手机适配 */
@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
    border-radius: 20px;
  }
  h1 {
    font-size: 28px;
  }
}
</style>
