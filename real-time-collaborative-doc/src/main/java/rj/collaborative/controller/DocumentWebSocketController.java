package rj.collaborative.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class DocumentWebSocketController {

    // ✅ 必须用 SimpMessagingTemplate 手动发送，@SendTo 不支持路径变量插值
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 接收客户端的 Yjs doc update（Base64 编码的二进制），原样广播给同文档所有订阅者
     *
     * 前端发送路径：/app/edit/{docId}
     * 前端订阅路径：/topic/{docId}
     */
    @MessageMapping("/edit/{docId}")
    public void handleYjsUpdate(
            @DestinationVariable String docId,
            String base64Update,                    // ✅ 前端用 Base64 字符串传输二进制
            SimpMessageHeaderAccessor headerAccessor) {

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            log.warn("WebSocket 会话无用户名，拒绝处理 docId: {}", docId);
            return;
        }

        if (base64Update == null || base64Update.isEmpty()) {
            log.debug("收到空 update，忽略 - 用户：{}, 文档：{}", username, docId);
            return;
        }

        log.debug("收到 Yjs update - 用户：{}, 文档：{}, Base64长度：{}", username, docId, base64Update.length());

        // ✅ 广播给该文档所有订阅者（包括发送者自己，前端用 origin === 'remote' 过滤）
        messagingTemplate.convertAndSend("/topic/" + docId, base64Update);
    }

    /**
     * 接收客户端的 Yjs Awareness update（光标位置、在线状态），广播给同文档所有订阅者
     *
     * 前端发送路径：/app/awareness/{docId}
     * 前端订阅路径：/topic/{docId}/awareness
     */
    @MessageMapping("/awareness/{docId}")
    public void handleAwarenessUpdate(
            @DestinationVariable String docId,
            String base64Update,
            SimpMessageHeaderAccessor headerAccessor) {

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            return;
        }

        if (base64Update == null || base64Update.isEmpty()) {
            return;
        }

        log.debug("收到 Awareness update - 用户：{}, 文档：{}", username, docId);

        messagingTemplate.convertAndSend("/topic/" + docId + "/awareness", base64Update);
    }
}