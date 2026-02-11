package rj.collaborative.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import rj.collaborative.service.OnlineUserService;

import java.util.Set;

@Controller
@Slf4j
@RequiredArgsConstructor
public class OnlineController {

    private final OnlineUserService onlineUserService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/online/{docId}")
    public void requestOnlineUsers(@DestinationVariable String docId) {
        Set<String> users = onlineUserService.getOnlineUsers(docId);
        messagingTemplate.convertAndSend("/topic/" + docId + "/online", users);
        log.info("[OnlineController] 手动请求在线用户列表，docId={}, 当前: {}", docId, users);
    }
}