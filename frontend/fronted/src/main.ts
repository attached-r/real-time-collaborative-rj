import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './assets/main.css'

import App from './App.vue'
import router from './router'

import Toast from 'vue3-toastify'
import 'vue3-toastify/dist/index.css'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 移除 Quill 相关导入和注册
// import { QuillEditor } from '@vueup/vue-quill'
// import '@vueup/vue-quill/dist/vue-quill.snow.css'
// import 'quill/dist/quill.core.css'

// Tiptap 不需要全局注册，在组件内使用 useEditor 即可
// import { Editor } from '@tiptap/core'  ← 也不需要在这里导入

const app = createApp(App)

app.use(createPinia())
app.use(router)

// Toast 配置
app.use(Toast, {
  autoClose: 5000,
  position: 'top-right',
  transition: 'bounce',
  closeOnClick: true,
  pauseOnHover: true,
  draggable: true,
  hideProgressBar: false,
  theme: 'colored',
})

// Element Plus
app.use(ElementPlus)

// 移除 Quill 组件全局注册
// app.component('QuillEditor', QuillEditor)

app.mount('#app')

