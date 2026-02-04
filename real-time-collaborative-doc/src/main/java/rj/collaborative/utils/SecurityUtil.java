package rj.collaborative.utils;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtil {

    /**
     * 获取当前登录用户的用户名（principal）
     * - 支持 Spring Security 的 UserDetails 对象
     * - fallback 处理匿名用户或其他情况
     * - 推荐在 Controller / Service 中统一调用，避免重复代码
     */
    public static String getCurrentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;  // 未登录
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        } else if (principal instanceof String username) {
            return username;  // 兼容匿名用户或其他 String principal
        } else {
            return principal.toString();  // 最坏情况 fallback
        }
    }

    /**
     * 检查是否已登录（可选辅助方法）
     */
    public static boolean isAuthenticated() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() &&
                !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()));
    }
}