package rj.collaborative.security;

import io.jsonwebtoken.lang.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rj.collaborative.service.OnlineUserService;

import java.util.Map;

/**
 * JWT通道拦截器，用于WebSocket连接的JWT令牌认证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final OnlineUserService onlineUserService;  // 新增注入

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("[JWT Interceptor] 收到 CONNECT 帧");

            String authHeader = accessor.getFirstNativeHeader("Authorization");
            log.info("[JWT Interceptor] Authorization header: {}", authHeader);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[JWT Interceptor] 缺少或格式错误的 Authorization header");
                return message;  // 允许继续（测试阶段）
            }

            String token = authHeader.substring(7);
            log.info("[JWT Interceptor] 提取 token: {}", token.substring(0, 20) + "...");

            try {
                if (!jwtTokenProvider.validateToken(token)) {
                    log.warn("[JWT Interceptor] JWT 令牌无效");
                    return message;
                }

                String username = jwtTokenProvider.getUsernameFromToken(token);
                log.info("[JWT Interceptor] 解析用户名: {}", username);

                Map<String, Object> attrs = accessor.getSessionAttributes();
                if (attrs != null) {
                    attrs.put("username", username);
                    log.info("[JWT Interceptor] 已存用户名到 sessionAttributes");
                } else {
                    log.warn("[JWT Interceptor] sessionAttributes 为 null，无法存用户名");
                }

                Authentication auth = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.info("[JWT Interceptor] SecurityContext 已设置，用户: {}", username);
            } catch (Exception e) {
                log.error("[JWT Interceptor] 处理 CONNECT 时异常", e);
            }
        }

        // 【新增】处理 SUBSCRIBE（用户进入文档）
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/")) {
                String docId = destination.substring("/topic/".length());
                String username = (String) accessor.getSessionAttributes().get("username");

                if (username != null && !docId.isBlank()) {
                    onlineUserService.addUser(docId, username);
                }
            }
        }

        // 【新增】处理 DISCONNECT（用户断开连接）
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String username = (String) accessor.getSessionAttributes().get("username");
            if (username != null) {
                log.info("[JWT Interceptor] 用户 {} 断开连接，建议清理在线状态", username);
                // 这里可以广播用户离线，或留给 Redis 过期清理
            }
        }

        return message;
    }
}