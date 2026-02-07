package rj.collaborative.security;

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
/**
 * JWT通道拦截器，用于WebSocket连接的JWT令牌认证
 * 实现在STOMP CONNECT命令时验证JWT令牌并建立安全上下文
 */
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

        // 只处理CONNECT命令
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // 验证Authorization头部是否存在且格式正确
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // 验证JWT令牌有效性
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    Authentication auth = new UsernamePasswordAuthenticationToken(username, null, null);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    System.out.println("WebSocket 认证成功，用户: " + username);
                } else {
                    throw new IllegalArgumentException("Invalid JWT token");
                }
            } else {
                throw new IllegalArgumentException("No JWT token provided in CONNECT frame");
            }
        }

        return message;
    }
}