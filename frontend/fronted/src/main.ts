import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import Toast from 'vue3-toastify'
import 'vue3-toastify/dist/index.css'

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
  theme: 'colored'
})
