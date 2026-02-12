package rj.collaborative.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rj.collaborative.dto.UserOnlineEvent;
import rj.collaborative.service.OnlineUserService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JWT WebSocket通道拦截器
 * 处理WebSocket连接的JWT认证和用户在线状态管理
 * 
 * @author collaborative-system
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final OnlineUserService onlineUserService;
    private final ApplicationContext applicationContext;

    /**
     * 拦截WebSocket消息，在消息发送前进行处理
     * 
     * @param message STOMP消息对象
     * @param channel 消息通道
     * @return 处理后的消息
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("[JWT Interceptor] 收到 CONNECT 帧");

            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return message;
            }

            String token = authHeader.substring(7);
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null) {
                        attrs.put("username", username);
                        // 初始化 subscribedDocs Set
                        attrs.put("subscribedDocs", new HashSet<String>());
                    }

                    Authentication auth = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("[JWT] 用户 {} WebSocket 连接认证成功", username);
                }
            } catch (Exception e) {
                log.error("[JWT] CONNECT 处理异常", e);
            }
        }

        // ==================== 2. SUBSCRIBE（进入文档）================
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/")) {
                String docId = destination.substring("/topic/".length());
                String username = getUsername(accessor);

                if (username != null && !docId.isBlank()) {
                    // 记录该 session 订阅的文档（支持多文档）
                    Set<String> subscribedDocs = getSubscribedDocs(accessor);
                    subscribedDocs.add(docId);

                    onlineUserService.addUser(docId, username);
                    log.info("[JWT] 用户 {} 进入文档 {}", username, docId);

                    applicationContext.publishEvent(new UserOnlineEvent(docId,username));  // 你原来的事件
                    // 推荐：未来可改成带 username 的事件
                }
            }
        }

        // ==================== 3. UNSUBSCRIBE（离开单个文档）================
        if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/")) {
                String docId = destination.substring("/topic/".length());
                String username = getUsername(accessor);

                if (username != null && !docId.isBlank()) {
                    Set<String> subscribedDocs = getSubscribedDocs(accessor);
                    subscribedDocs.remove(docId);

                    onlineUserService.removeUser(docId, username);
                    log.info("[JWT] 用户 {} 取消订阅文档 {}（UNSUBSCRIBE）", username, docId);

                    applicationContext.publishEvent(new UserOnlineEvent(docId, username));
                }
            }
        }

        // ==================== 4. DISCONNECT（整个连接断开）================
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String username = getUsername(accessor);
            if (username == null) return message;

            Set<String> subscribedDocs = getSubscribedDocs(accessor);
            if (!subscribedDocs.isEmpty()) {
                for (String docId : new HashSet<>(subscribedDocs)) {  // 复制避免并发修改
                    onlineUserService.removeUser(docId, username);
                    log.info("[JWT] 用户 {} 断开连接，移除文档 {} 的在线状态", username, docId);
                    applicationContext.publishEvent(new UserOnlineEvent(docId, username));
                }
                // 清空
                accessor.getSessionAttributes().remove("subscribedDocs");
            } else {
                log.info("[JWT] 用户 {} 断开连接，但未订阅任何文档", username);
            }
        }

        return message;
    }

    // ==================== 辅助方法 ====================
    // 从 session 属性中获取用户名
    private String getUsername(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get("username") : null;
    }
    // 从 session 获取订阅的文档
    @SuppressWarnings("unchecked")  // 忽略类型转换警告的注解
    private Set<String> getSubscribedDocs(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return new HashSet<>();

        return (Set<String>) attrs.computeIfAbsent("subscribedDocs", k -> new HashSet<String>());
    }
}
