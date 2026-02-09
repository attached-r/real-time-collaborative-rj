package rj.collaborative.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * JWT通道拦截器，用于WebSocket连接的JWT令牌认证
 * 实现在STOMP CONNECT命令时验证JWT令牌并建立安全上下文
 */
@Slf4j
@Component // 注册为 Spring Bean
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;  // 生成JWT令牌工具类

    /**
     * 在消息发送前进行拦截处理，主要处理CONNECT命令的JWT认证
     * @param message 要发送的消息对象
     * @param channel 消息通道
     * @return 处理后的消息对象
     * @throws IllegalArgumentException 当JWT令牌无效或缺失时抛出异常
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
                // 临时不抛异常，让连接继续（测试用）
                return message;
            }

            String token = authHeader.substring(7);
            log.info("[JWT Interceptor] 提取 token: {}", token.substring(0, 20) + "...");

            try {
                if (!jwtTokenProvider.validateToken(token)) {
                    log.warn("[JWT Interceptor] JWT 令牌无效");
                    return message;  // 临时不抛
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

                Authentication auth = new UsernamePasswordAuthenticationToken(username, null, null);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.info("[JWT Interceptor] SecurityContext 已设置，用户: {}", username);
            } catch (Exception e) {
                log.error("[JWT Interceptor] 处理 CONNECT 时异常", e);
                // 临时不抛，让连接继续
            }
        }

        return message;
    }
}