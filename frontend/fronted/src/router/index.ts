import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DocumentListView from '../views/DocumentListView.vue'


const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 登录
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    // 列表展示页面
    {
      path: '/documents',
      name: 'documents',
      component: DocumentListView
    },
    //列表详情页面
    {
    path: '/documents/:id',
    name: 'DocumentDetail',
    component: () => import('../views/DocumentDetailView.vue')
  },
  // 注册页面
  {
  path: '/register',
  name: 'Register',
  component: () => import('../views/RegisterView.vue')
  },
  //编辑页面
  {
  path: '/edit/:id',
  name: 'EditDocument',
  component: () => import('../views/EditDocumentView.vue')
}
  ]
})


export default router
