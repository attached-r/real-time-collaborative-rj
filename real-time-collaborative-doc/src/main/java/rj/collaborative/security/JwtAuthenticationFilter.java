package rj.collaborative.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * 拦截器，验证请求中的 token
 * */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;  // 后面会实现

    @Override  // 拦截所有请求，protected是默认修饰符，表示该方法只能被当前类及子类访问
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 握手阶段可能不带 Authorization 头，放行，让 Stomp 拦截器处理
        String path=request.getRequestURI();
        if (path.startsWith("/ws/") || path.equals("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 1. 从请求头提取 token
        String token = getTokenFromRequest(request);
        log.info("收到请求 URL: {}, token: {}", request.getRequestURI(), token != null ? "存在" : "缺失");
        // 2. 验证 token 有效性
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            log.info("token 验证成功");
            // 2.1. 从 token 中提取用户名
            String username = jwtTokenProvider.getUsernameFromToken(token);

            // 2.2. 加载用户信息 -> 认证主体
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 2.3. 创建认证对象
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // 2.4. 设置认证对象
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 2.5.设置到安全上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }else {
            log.info("token 验证失败{}", token);
        }
        log.info("请求头 Authorization: {}", request.getHeader("Authorization"));
        // 3. 放行
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 token  方法
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
/**
 * 安全机制流程
 *     A[请求到达] --> B[JWT过滤器验证Token]
 *     B --> C[创建Authentication对象]
 *     C --> D[存储到SecurityContext]
 *     D --> E[后续过滤器访问SecurityContext]
 *     E --> F[获取当前认证用户]
 *     F --> G[权限检查DecisionManager]
 *     G --> H[允许或拒绝访问]
 *
 *
 * 第二个函数
 * 功能是从HTTP请求头中提取JWT Token：
 *
 * 1. 获取Authorization请求头的值
 * 2. 检查是否存在且以"Bearer "开头
 * 3. 如果符合格式，返回去除"Bearer "前缀后的实际token
 * 4. 否则返回null
 *
 * 这是标准的JWT Bearer Token提取逻辑。
 * */
