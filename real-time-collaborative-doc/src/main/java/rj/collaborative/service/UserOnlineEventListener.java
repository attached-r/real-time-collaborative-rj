package rj.collaborative.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import rj.collaborative.dto.UserOnlineEvent;

import java.util.ArrayList;
import java.util.Set;

//监听事件
@Component
@RequiredArgsConstructor
@Slf4j
public class UserOnlineEventListener {

    private final OnlineUserService onlineUserService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void init() {
        log.info("UserOnlineEventListener 已启动，可以接收事件");
    }

    @EventListener
    public void handleUserOnlineEvent(UserOnlineEvent event) {
        String docId = event.getDocId();
        Set<String> users = onlineUserService.getOnlineUsers(docId);
        // 在 Listener 里改成 List
        messagingTemplate.convertAndSend("/topic/" + docId + "/online", new ArrayList<>(users));
        log.info("广播在线用户成功，docId={}, 在线人数={}", docId, users.size());
    }
}