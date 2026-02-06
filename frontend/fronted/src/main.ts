import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './assets/main.css'

import App from './App.vue'
import router from './router'

import Toast from 'vue3-toastify'
import 'vue3-toastify/dist/index.css'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import QuillEditor from 'vue-quill-editor'
import 'quill/dist/quill.core.css'
import 'quill/dist/quill.snow.css'
// 挂载
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')

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

app.use(ElementPlus)

app.component('QuillEditor', QuillEditor)
