package rj.collaborative.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.User;
import rj.collaborative.repository.UserRepository;

import java.util.Collections;
/**
 * 自定义 UserDetailsService 实现
 * 逻辑：
 * CustomUserDetailsService.loadUserByUsername()
 *     ↓
 * userRepository.findByUsername(username)
 *     ↓
 * MongoDB Driver 执行操作
 *     ↓
 * 数据查询到 MongoDB
 *     ↓
 * 构建 UserDetails 对象
 *     ↓
 * 返回给 Spring Security
 */
@Service  // 必须加 @Service，让 Spring 扫描成 Bean
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),  // 已 BCrypt 加密的密码
                true, true, true, true,  // 账户未过期/未锁定等
                Collections.emptyList()  // 角色列表，暂时空，后期加 roles
        );
    }
}