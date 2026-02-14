package rj.collaborative.controller;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import java.util.HashMap;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rj.collaborative.dto.PatchRequest;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;
import rj.collaborative.service.DocumentService;
import rj.collaborative.utils.SecurityUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档控制器
 * 处理文档的创建、查询、更新、删除等操作，以及实时协作编辑功能
 * 
 * @author collaborative-system
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentRepository documentRepository;

    /**
     * 创建新文档
     * 
     * @param request 包含title和content的请求体
     * @return 创建成功的文档信息
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");

        if (StringUtils.isBlank(title)) {
            return ResponseEntity.badRequest().body(Map.of("message", "标题不能为空"));
        }

        DocumentEntity doc = documentService.create(title, content);
        log.info("创建文档成功 - ID: {}", doc.getId());

        Map<String, String> response = Map.of(
                "id", doc.getId().toString(),
                "message", "创建成功"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前用户的所有文档列表
     * 
     * @return 文档列表
     */
    @GetMapping
    public List<DocumentEntity> getDocuments() {
        String username = SecurityUtil.getCurrentUsername();
        log.info("当前登录用户: {}", username);

        List<DocumentEntity> list = documentService.listByUser(username);
        log.info("查询到文档数量: {}", list.size());

        return list;
    }

    /**
     * 获取指定文档详情
     * 
     * @param id 文档ID
     * @return 文档详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentEntity> getDocumentById(@PathVariable String id) {
        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        if (optionalDoc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DocumentEntity doc = optionalDoc.get();
        String currentUserId = SecurityUtil.getCurrentUsername();

        try {
            documentService.checkAccess(doc);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(null);
        }
        return ResponseEntity.ok(doc);
    }

    /**
     * 搜索文档
     * 
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    @GetMapping("/search")
    public List<DocumentEntity> searchDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String username = SecurityUtil.getCurrentUsername();
        log.info("搜索文档 - 用户: {}, 关键词: {}", username, keyword);

        return documentService.searchByUserAndKeyword(username, keyword, page, size);
    }

    /**
     * 删除文档
     * 
     * @param id 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable String id) {
        String username = SecurityUtil.getCurrentUsername();
        log.info("删除文档请求 - ID: {}, 用户: {}", id, username);

        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        if (optionalDoc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DocumentEntity doc = optionalDoc.get();
        if (!doc.getOwnerId().equals(username)) {
            return ResponseEntity.status(403).body("无权限删除此文档");
        }

        documentService.deleteById(id);
        log.info("删除成功 - ID: {}", id);

        return ResponseEntity.ok("删除成功");
    }

    /**
     * 更新文档
     * 
     * @param id 文档ID
     * @param request 更新内容
     * @return 更新后的文档
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentEntity> updateDocument(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {

        String username = SecurityUtil.getCurrentUsername();
        log.info("更新文档请求 - ID: {}, 用户: {}, 请求体: {}", id, username, request);

        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        if (optionalDoc.isEmpty()) {
            log.warn("文档不存在: {}", id);
            return ResponseEntity.notFound().build();
        }

        DocumentEntity doc = optionalDoc.get();

        try {
            documentService.checkAccess(doc);
        } catch (SecurityException e) {
            log.warn("无权限更新文档 - 用户: {}, 文档ID: {}", username, id);
            return ResponseEntity.status(403).build();
        }

        boolean hasUpdate = false;

        if (request.containsKey("title")) {
            String newTitle = (String) request.get("title");
            if (newTitle != null) {
                doc.setTitle(newTitle);
                hasUpdate = true;
                log.debug("标题更新为: {}", newTitle);
            }
        }

        if (request.containsKey("content")) {
            Object newContentObj = request.get("content");
            if (newContentObj instanceof String) {
                String text = (String) newContentObj;
                doc.setContent(new Document("text", text));
                hasUpdate = true;
                log.debug("content 更新为字符串，长度: {}", text.length());
            } else if (newContentObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) newContentObj;
                doc.setContent(new Document(map));
                hasUpdate = true;
                log.debug("content 更新为复杂结构");
            } else {
                log.warn("不支持的 content 类型: {}", newContentObj != null ? newContentObj.getClass().getName() : "null");
            }
        }

        doc.setUpdatedAt(LocalDateTime.now());
        DocumentEntity saved = documentRepository.save(doc);

        log.info("强制保存成功 - ID: {}, 用户: {}, 新版本: {}", id, username, saved.getVersion());

        return ResponseEntity.ok(saved);
    }

    /**
     * 分享文档给其他用户
     * 
     * @param id 文档ID
     * @param body 目标用户名
     * @return 分享结果
     */
    @PostMapping("/{id}/share")
    public ResponseEntity<String> shareDocument(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String targetUsername = body.get("username");
        if (StringUtils.isBlank(targetUsername)) {
            return ResponseEntity.badRequest().body("缺少用户名");
        }

        try {
            documentService.shareWithUser(id, targetUsername);
            return ResponseEntity.ok("已成功分享给 " + targetUsername);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取当前用户所有可访问文档
     * 
     * @return 可访问的文档列表
     */
    @GetMapping("/my-accessible")
    public List<DocumentEntity> getMyAccessibleDocuments() {
        String username = SecurityUtil.getCurrentUsername();
        return documentService.getAccessibleDocuments(username);
    }

    /**
     * 处理文档实时编辑
     * 
     * @param docId 文档ID
     * @param content 编辑内容
     * @param headerAccessor 消息头访问器
     * @return 处理结果
     */
    @MessageMapping("/edit/{docId}")
    @SendTo("/topic/{docId}")   // 注意这里用 {docId} 占位符
    public String handleEdit(
            @DestinationVariable String docId,
            String content,   // 参数名改成 content，更直观（原来叫 delta）
            SimpMessageHeaderAccessor headerAccessor) {

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            log.warn("WebSocket 会话无用户名");
            return "{\"error\":\"未认证\"}";
        }

        log.info("收到编辑 - 用户:{}, 文档:{}, 内容长度:{}", username, docId, content.length());

        try {
            String updated = documentService.applyDelta(docId, content, headerAccessor);

            Map<String, Object> response = new HashMap<>();
            response.put("content", updated);
            response.put("sender", username);

            String json = new JSONObject(response).toString();
            log.info("即将广播 JSON: {}", json);  // ← 新增这一行
            return json;
        } catch (Exception e) {
            log.error("实时编辑处理失败", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 心跳检测端点
     */
    @GetMapping("/ping")
    public void handlePing() {
        log.debug("收到心跳请求");
    }
}