package rj.collaborative.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import rj.collaborative.security.JwtChannelInterceptor;

/**
 * WebSocket配置类，用于配置STOMP协议的WebSocket消息代理
 * 实现WebSocket消息代理的端点注册、消息路由和安全拦截
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Autowired
    private JwtChannelInterceptor jwtChannelInterceptor;  // Spring 自动注入
    /**
     * 注册STOMP端点，配置WebSocket连接入口
     * @param registry STOMP端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")                     // 前端连接地址：ws://localhost:8080/ws
                .setAllowedOriginPatterns("*")          // 开发允许所有域，生产改成前端地址
                .withSockJS();                          // 支持 SockJS 回退（浏览器兼容）
    }

    /**
     * 配置消息代理，设置消息路由规则
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");          // 客户端订阅前缀：/topic/xxx
        registry.setApplicationDestinationPrefixes("/app");  // 客户端发送消息前缀：/app/xxx
        registry.setUserDestinationPrefix("/user");     // 用户专属消息：/user/queue/xxx
    }

    /**
     * 配置客户端入站通道，添加JWT认证拦截器
     * 拦截CONNECT帧进行JWT令牌验证
     * @param registration 通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

}