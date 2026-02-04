//该配置文件是用来解决
// TypeScript 默认不认识 .vue 文件（它只认识 .ts / .js）
//Vue 项目需要告诉 TS：.vue 文件可以被导入，并且类型是 DefineComponent 或 Vue 组件

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
