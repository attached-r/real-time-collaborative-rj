# 修复多人实时协作编辑的核心 Bug

## 问题背景

项目的多人实时协作编辑功能基于 **Yjs CRDT** + **STOMP over WebSocket** 架构实现，但在实际使用中完全无法正常工作。经排查，存在 5 个关键问题，涵盖前端连接、数据传输、同步机制和后端拦截器等多个层面。

---

## 问题 1：WebSocket 连接端口错误（致命阻塞）

**现象**：前端 WebSocket 一直连不上，控制台显示 SockJS 连接失败。

**原因**：后端 Spring Boot 配置运行在端口 **8081**（`application.yml:17`），但前端代码写死了连接地址：

```typescript
// ❌ 错误：端口 8080 上没有服务在运行
const socket = new SockJS('http://localhost:8080/ws')
```

**修复**：改为使用相对路径，通过 Vite 代理转发到后端：

```typescript
// ✅ 正确：走 Vite proxy（vite.config.ts 已配置 /ws → localhost:8081）
const socket = new SockJS('/ws')
```

Vite 代理配置（`vite.config.ts`）已包含 `/ws` 的 WebSocket 转发规则，使用相对路径还能避免跨域问题。

---

## 问题 2：Base64 编解码在大更新时栈溢出

**现象**：编辑内容较多时，浏览器控制台报错，实时同步中断。

**原因**：编解码函数使用展开运算符将 `Uint8Array` 的所有元素一次性传给 `String.fromCharCode`：

```typescript
// ❌ 错误：bytes 超过约 125K 时触发调用栈溢出
const toBase64 = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes))
```

**修复**：改为分块处理，每块 8KB：

```typescript
const toBase64 = (bytes: Uint8Array): string => {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  }
  return btoa(binary)
}

const fromBase64 = (b64: string): Uint8Array => {
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}
```

---

## 问题 3：Awareness（光标/用户状态）初始状态未广播

**现象**：新加入的用户在其他用户的界面上不显示，直到该用户移动一次光标才出现。

**原因**：Awareness 状态在模块加载时就设置了：

```typescript
// 组件模块加载时执行，此时 WebSocket 未连接
awareness.setLocalStateField('user', {
  name:  currentUsername,
  color: randomColor,
})
```

但 WebSocket 的 `onConnect` 回调此时还未触发，`awareness.on('update', ...)` 事件监听也未注册。初始状态设置后从未被广播到其他客户端。

**修复**：在 STOMP 连接成功后的 `onConnect` 回调末尾，主动广播当前 Awareness 状态：

```typescript
const initAwareness = awarenessProtocol.encodeAwarenessUpdate(awareness, [ydoc.clientID])
stompClient.value!.publish({
  destination: `/app/awareness/${docId}`,
  body: toBase64(initAwareness),
})
```

---

## 问题 4：新用户无法同步 Yjs CRDT 状态（架构缺陷）

**现象**：用户 A 编辑文档后，用户 B 加入，B 只能看到数据库中保存的纯文本内容，看不到 A 未保存的修改。不同客户端之间的 Yjs CRDT 状态不一致。

**原因**：原有架构中，Yjs 的所有 CRDT 操作历史仅存在于各客户端的内存中，服务器不保存任何 Yjs 状态。新用户加入时只能从 REST API 获取纯文本并创建全新的 Yjs 文档，缺失了所有历史 CRDT 操作记录。

**修复方案**：在保存文档时将完整的 Yjs CRDT 状态快照持久化到 MongoDB，新用户加载时直接恢复该状态。

### 4a. 后端 — DocumentEntity 新增字段

```java
// 文件：entity/DocumentEntity.java
private byte[] yjsState;  // Yjs 完整状态快照（Y.encodeStateAsUpdate 的二进制输出）
```

### 4b. 后端 — DocumentController 保存/读取 yjsState

在 `updateDocument()` 中解码前端传来的 Base64 字符串：

```java
if (request.containsKey("yjsState")) {
    String yjsStateBase64 = (String) request.get("yjsState");
    if (yjsStateBase64 != null && !yjsStateBase64.isEmpty()) {
        doc.setYjsState(Base64.getDecoder().decode(yjsStateBase64));
        hasUpdate = true;
    }
}
```

`getDocumentById()` 直接返回 entity，Jackson 自动将 `byte[]` 序列化为 Base64 字符串。

### 4c. 前端 — 加载时恢复 Yjs 状态

```typescript
const yjsStateBase64: string | undefined = res.data.yjsState
if (yjsStateBase64) {
  // 优先从 Yjs 状态快照恢复（包含完整 CRDT 历史）
  Y.applyUpdate(ydoc, fromBase64(yjsStateBase64), 'remote')
} else if (ytext.length === 0 && initialText) {
  // 没有 yjsState，回退到纯文本初始化
  ydoc.transact(() => { ytext.insert(0, initialText) }, 'init')
}
```

### 4d. 前端 — 保存时发送 Yjs 状态

```typescript
await api.put(`/api/documents/${doc.value!.id}`, {
  title:    doc.value!.title,
  content:  plainText,
  yjsState: toBase64(Y.encodeStateAsUpdate(ydoc)),
})
```

---

## 问题 5：UNSUBSCRIBE 帧处理失效

**现象**：用户离开文档后仍显示在在线列表中，直到断开整个 WebSocket 连接才消失。

**原因**：STOMP 协议的 `UNSUBSCRIBE` 帧使用 `id`（订阅 ID）头而非 `destination` 头，但拦截器使用了 `accessor.getDestination()` 来获取目标地址：

```java
// ❌ 错误：UNSUBSCRIBE 帧没有 destination 头，始终返回 null
if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
    String destination = accessor.getDestination();
```

**修复**：在 `SUBSCRIBE` 时建立 `subscriptionId → docId` 映射，`UNSUBSCRIBE` 时通过 `subscriptionId` 查找：

```java
// SUBSCRIBE 时记录映射
String subscriptionId = accessor.getSubscriptionId();
if (subscriptionId != null) {
    accessor.getSessionAttributes().put("sub_" + subscriptionId, docId);
}

// UNSUBSCRIBE 时通过 subscriptionId 查找 docId
String subscriptionId = accessor.getSubscriptionId();
String docId = (String) attrs.get("sub_" + subscriptionId);
```

---

## 涉及修改的文件

| 文件 | 修改内容 |
|------|----------|
| `frontend/fronted/src/views/EditDocumentView.vue` | WebSocket URL、Base64 编解码、Awareness 广播、Yjs 状态同步 |
| `.../entity/DocumentEntity.java` | 新增 `yjsState` 字段 |
| `.../controller/DocumentController.java` | 新增 Base64 导入，处理 yjsState 的读写 |
| `.../security/JwtChannelInterceptor.java` | 修复 UNSUBSCRIBE 订阅 ID 映射逻辑 |

---

## 验证步骤

1. 启动后端：`mvn spring-boot:run`（端口 8081）
2. 启动前端：`npm run dev`（端口 5173）
3. 用两个浏览器窗口分别登录不同用户
4. 打开同一文档，确认：
   - WebSocket 状态显示「已连接」
   - 双方在线人数 > 1，能看到对方用户名
   - 一方输入文字，另一方实时看到变化
   - 彩色光标同步显示
   - 保存后，新用户打开文档能看到完整的已有内容
   - 编辑大量文本后无控制台栈溢出报错
