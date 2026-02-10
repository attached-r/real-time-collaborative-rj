package rj.collaborative.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String ONLINE_KEY_PREFIX = "online:";

    /**
     * 用户进入文档 → 添加到在线集合
     * @param docId 文档ID
     * @param username 用户名
     */
    public void addUser(String docId, String username) {
        String key = ONLINE_KEY_PREFIX + docId;
        redisTemplate.opsForSet().add(key, username);
        log.info("用户 {} 进入文档 {}，当前在线: {}", username, docId, getOnlineUsers(docId));
    }

    /**
     * 用户离开文档 → 从在线集合移除
     * @param docId 文档ID
     * @param username 用户名
     */
    public void removeUser(String docId, String username) {
        String key = ONLINE_KEY_PREFIX + docId;
        redisTemplate.opsForSet().remove(key, username);
        log.info("用户 {} 离开文档 {}，当前在线: {}", username, docId, getOnlineUsers(docId));
    }

    /**
     * 获取文档当前在线用户列表
     * @param docId 文档ID
     * @return 在线用户名集合
     */
    public Set<String> getOnlineUsers(String docId) {
        String key = ONLINE_KEY_PREFIX + docId;
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(String docId, String username) {
        String key = ONLINE_KEY_PREFIX + docId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, username));
    }
}