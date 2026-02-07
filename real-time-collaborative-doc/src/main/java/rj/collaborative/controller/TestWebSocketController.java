package rj.collaborative.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/ws")
public class TestWebSocketController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate; // 类用于发送消息

    @GetMapping("/test")
    public String testBroadcast() {
        //
        messagingTemplate.convertAndSend("/topic/test", "测试广播消息: " + LocalDateTime.now());
        return "已发送";
    }
}
