package rj.collaborative.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import rj.collaborative.security.JwtChannelInterceptor;

/**
 * WebSocket配置类
 * 配置STOMP协议的WebSocket消息代理，包括端点注册、消息路由和安全拦截
 * 
 * @author collaborative-system
 * @since 1.0
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Autowired
    private JwtChannelInterceptor jwtChannelInterceptor;

    /**
     * 注册STOMP端点，配置WebSocket连接入口
     * 
     * @param registry STOMP端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 【注意】WebSocket连接地址：ws://localhost:8080/ws
        // 生产环境应限制allowedOrigins为具体域名
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 配置消息代理，设置消息路由规则
     * 
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单消息代理，处理以/topic开头的目标
        registry.enableSimpleBroker("/topic");
        
        // 设置应用程序目的地前缀，客户端发送消息需以/app开头
        registry.setApplicationDestinationPrefixes("/app");
        
        // 设置用户专属消息前缀
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 配置客户端入站通道，添加JWT认证拦截器
     * 
     * @param registration 通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    /**
     * 配置WebSocket传输参数
     * 
     * @param registration WebSocket传输注册器
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 设置消息大小限制为1280KB
        registration.setMessageSizeLimit(1280 * 1024);
        
        // 设置发送超时时间为15秒
        registration.setSendTimeLimit(15 * 1000);
        
        // 设置发送缓冲区大小限制为512KB
        registration.setSendBufferSizeLimit(512 * 1024);
    }
}