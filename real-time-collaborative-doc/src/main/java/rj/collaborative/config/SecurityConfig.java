package rj.collaborative.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import rj.collaborative.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {
    /**
     * 配置密码加密器
     * 使用 BCrypt 算法进行密码加密
     * 手动创建 BCryptPasswordEncoder 对象并返回
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 JWT 认证过滤器
     * 创建 JwtAuthenticationFilter 对象并返回
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    /**
     * 配置 Spring Security 过滤器链
     * 创建 SecurityFilterChain 对象并返回
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  // 关闭 CSRF 保护
                // 关闭 Spring Security 的会话管理，因为我们使用 JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置权限
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/hello", "/test-*").permitAll()  // 登录注册 + 测试放行
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);  // 加 JWT 过滤器，放在用户名密码过滤器之前

        return http.build();
    }

    /**
     * 配置 AuthenticationManager
     * 创建 AuthenticationManager  认证管理器 对象并返回
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

/**
 * 用户登录阶段
 *     A[用户提交登录请求] --> B[AuthController.login()]
 *     B --> C[AuthenticationManager.authenticate()]
 *     C --> D[调用 UserDetailsService.loadUserByUsername()]
 *     D --> E[从数据库查询用户]
 *     E --> F[验证密码]
 *     F --> G[JwtTokenProvider.generateToken()]
 *     G --> H[生成 JWT Token]
 *     H --> I[返回 Token 给客户端]
 *
 * 后续请求验证阶段
 *     A[客户端携带 JWT Token 请求] --> B[JwtAuthenticationFilter 拦截]
 *     B --> C[从 Header 提取 Bearer Token]
 *     C --> D[JwtTokenProvider.validateToken()]
 *     D --> E[验证 Token 有效性]
 *     E -->|有效| F[JwtTokenProvider.getUsernameFromToken()]
 *     F --> G[获取用户名]
 *     G --> H[UserDetailsService.loadUserByUsername()]
 *     H --> I[加载用户详情]
 *     I --> J[创建 Authentication 对象]
 *     J --> K[设置到 SecurityContext]
 *     K --> L[继续执行业务逻辑]
 *     E -->|无效| M[返回 401 未授权]
 */