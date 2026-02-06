<template>
  <div class="app-container">
    <!-- 主题切换按钮（全局右上角） -->
    <button class="theme-toggle" @click="toggleTheme">
      {{ isDark ? '☀️ 亮' : '🌙 暗' }}
    </button>

    <router-view />
  </div>
</template>

<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'

const { isDark, toggleTheme } = useTheme()
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  background: var(--bg-color);
  color: var(--text-color);
}

.theme-toggle {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 999;
  padding: 8px 16px;
  background: rgba(255,255,255,0.8);
  border: 1px solid var(--border-color);
  border-radius: 50px;
  font-size: 16px;
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition: all 0.3s;
}

.dark .theme-toggle {
  background: rgba(31,41,55,0.8);
  color: white;
}

.theme-toggle:hover {
  transform: scale(1.1);
}
</style>
