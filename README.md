
# 在线实时协作文本编辑器

一个前后端分离的在线文档协作工具，支持多人实时编辑、权限控制、在线用户显示、文档 CRUD 。类似简化版石墨文档 / Notion。

**项目地址**：https://github.com/attached-r/real-time-collaborative-rj/tree/main/real-time-collaborative-doc

## 技术栈

### 后端
- Spring Boot 3.x
- Spring Data MongoDB（文档存储 + 嵌入式版本历史）
- Spring Data Redis（在线用户统计）
- Spring Security + JWT（前后端分离认证）
- WebSocket + STOMP（实时广播）
- Lombok、JJWT、Jackson

### 前端
- Vue 3 + Composition API
- Quill.js（富文本编辑器）
- @stomp/stompjs + sockjs-client（WebSocket 客户端）
- Axios（API 请求）
- vue3-toastify（提示）

### 数据库 & 缓存
- MongoDB Atlas（主存储）
- Redis（Docker 部署，在线用户 Set）

## 核心功能

- 用户注册 / 登录 / JWT 认证
- 文档创建 / 列表 / 编辑 / 删除 / 分享
- 多人实时编辑（WebSocket 广播变更）
- 在线用户数实时统计（Redis Set）
- 文档权限控制（拥有者 + 协作者）

## 项目结构

```
real-time-collaborative-doc (后端)
├── src/main/java/rj/collaborative
│   ├── entity          用户、文档、版本实体
│   ├── repository      MongoDB Repository
│   ├── service         业务逻辑（文档、在线用户）
│   ├── controller      REST API + WebSocket 消息处理
│   ├── security        JWT + Filter
│   └── config          WebSocket 配置
├── application.yml     配置（MongoDB、Redis、JWT）
└── pom.xml

frontend/fronted (前端)
├── src
│   ├── api             Axios 封装
│   ├── views           文档列表、编辑页
│   ├── App.vue
│   └── main.js
└── package.json
```

## 快速启动

### 后端

1. 配置 `application.yml` 中的 MongoDB URI、Redis 地址、JWT secret
2. 启动 Redis（Docker）：
   ```bash
   docker run -d -p 6379:6379 redis
   ```
3. 运行项目：
   ```bash
   mvn spring-boot:run
   ```
   默认端口：8080

### 前端

1. 安装依赖：
   ```bash
   cd frontend/fronted
   npm install
   ```
2. 启动：
   ```bash
   npm run dev
   ```
   默认端口：5173

访问 `http://localhost:5173` 即可使用。

## 实时协作实现说明

- 使用 WebSocket + STOMP 协议实现文档内容实时广播
- 前端 Quill 编辑器监听 `text-change`，发送变更到后端
- 后端合并后广播最新内容（当前为简单追加式，后续计划引入 CRDT / OT 机制解决并发冲突）
- 在线用户通过 Redis Set 实时统计与广播

## 未来优化方向

~~- 实现真正的 OT / CRDT 合并（Yjs 或 diff-match-patch）~~
- 添加 AI 文档总结（集成 DeepSeek / OpenAI API）
- 支持 PDF 导出（OpenPDF）
- 完善版本回滚功能
~~- 优化前端 UI（暗黑模式、响应式）~~

## 联系方式

- GitHub: https://github.com/attached-r
- Email: `heidashuai052@gmail`
- 欢迎 Star & Fork & Issue

最后更新：2026年2月
