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
import java.util.Map;

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
            log.info("[JWT Interceptor] Authorization header: {}", authHeader);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[JWT Interceptor] 缺少或格式错误的 Authorization header");
                return message;
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
                    log.info("[JWT Interceptor] 已调用 addUser，docId={}, username={}", docId, username);

                    // 【关键】发布用户上线事件
                    try {
                        applicationContext.publishEvent(new UserOnlineEvent(docId));
                        log.info("[JWT Interceptor] UserOnlineEvent 发布成功，docId={}", docId);
                    } catch (Exception e) {
                        log.error("[JWT Interceptor] 发布 UserOnlineEvent 失败", e);
                    }
                }
            }
        }

        // 【问题】DISCONNECT 处理不完整 - 缺少用户离线逻辑
        // 当前只记录日志，没有调用 onlineUserService.removeUser()
        // 这会导致Redis中用户状态无法及时清理
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String username = (String) accessor.getSessionAttributes().get("username");
            if (username != null) {
                log.info("[JWT Interceptor] 用户 {} 断开连接，建议清理在线状态", username);
                // TODO: 应该在这里调用 onlineUserService.removeUser(docId, username)
            }
        }

        return message;
    }
}