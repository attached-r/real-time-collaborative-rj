package rj.collaborative.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.User;
import rj.collaborative.repository.UserRepository;

import java.util.Optional;
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;  // 来自 SecurityConfig 的 BCrypt
    /**
     * 注册用户：加密密码并保存
     * 逻辑：
     * UserService.register()
     *     ↓
     * User.builder() 构建用户对象
     *     ↓
     * userRepository.save(user)
     *     ↓
     * MongoDB Driver 执行操作
     *     ↓
     * 数据持久化到 MongoDB
     */
    public User register(String username, String rawPassword, String email) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            log.info("用户名已存在");
            throw new RuntimeException("用户名已存在");
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))  // 加密
                .email(email)
                .build();
        log.info("保存用户：{}", user);
        return userRepository.save(user);
    }

    /**
     * 根据用户名查找用户
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}