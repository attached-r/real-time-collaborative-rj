package rj.collaborative.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    /**
     * 配置安全过滤器链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                        // 开发/测试端点全部放行
                        .requestMatchers(
                                "/hello",
                                "/test-*",              // 所有 /test- 开头的（如 /test-mongo, /test-register）
                                "/actuator/**",         // 监控端点
                                "/swagger-ui/**",       // 如果有 Swagger
                                "/v3/api-docs/**"       // OpenAPI
                        ).permitAll()
                        // 未来正式接口才认证（比如 /api/**）
                        .requestMatchers("/api/**").authenticated()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())  // 允许表单登录
                .httpBasic(Customizer.withDefaults()); // 允许 HTTP Basic 认证
        return http.build();  // 返回过滤器链
    }

    /**
     * 配置密码加密器
     * 使用 BCrypt 算法进行密码加密
     * 手动创建 BCryptPasswordEncoder 对象并返回
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
