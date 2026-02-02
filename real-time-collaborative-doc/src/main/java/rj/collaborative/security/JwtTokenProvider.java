package rj.collaborative.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
/**
 * 生成 JWT token 并带有获取用户名，验证是否有效的 方法
 */
@Component  // 注册为 Spring Bean
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;  // 从 application.yml 读取

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;  // 过期时间 ms

    // 生成密钥（生产环境用固定密钥，这里用字符串转 SecretKey）
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成 JWT token
     */
    public String generateToken(Authentication authentication) { // Authentication 对象
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)          // 主题
                .issuedAt(now)              // 创建时间
                .expiration(expiryDate)      // 过期时间
                .signWith(getSigningKey())  // 用 HMAC-SHA256 算法签名
                .compact();  // 生成 JWT token
    }

    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())  // 验证签名
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())  // 验证签名
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            // 可以加日志：MalformedJwtException, ExpiredJwtException 等
            return false;
        }
    }
}

/** Authentication 对象
 * 是 Spring Security 的核心接口，主要属性和方法包括：
 *
 * 核心属性：
 * Principal - 认证主体（通常是 UserDetails 对象）
 * Credentials - 认证凭证（密码等敏感信息）
 * Authorities - 权限集合（GrantedAuthority 列表）
 * Details - 认证详情（如 IP 地址、session 信息等）
 *
 */