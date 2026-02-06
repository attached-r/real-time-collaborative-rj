package rj.collaborative.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;
import rj.collaborative.service.DocumentService;
import rj.collaborative.service.UserService;
import rj.collaborative.utils.SecurityUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
@Slf4j
@RestController
@RequestMapping("/api/documents")  // 所有接口前缀 /api/documents
public class DocumentController {

    @Autowired
    private DocumentService documentService;  // 注入 Service，复用业务逻辑

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * 创建文档（POST /api/documents）
     * - 接收 title + content（JSON body）
     * - 调用 Service.create 设置 ownerId（当前登录用户）
     * - 返回新文档 ID 或完整文档
     * - 权限：@Authenticated（SecurityConfig 已配置，需要 token）
     * - 详解：前端发 POST 带 body，后端自动校验认证（JWT Filter），Service 层处理创建逻辑
     * - 为什么这样写：分离 Controller（API 入口） + Service（业务），符合 MVC 分层
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");

        DocumentEntity doc = documentService.create(title, content);  // 调用 Service 创建
        log.info("创建文档：{}", doc.getId());
        return ResponseEntity.ok(Map.of("id", doc.getId(), "message", "创建成功"));  // 返回 ID 供前端用
    }

    /**
     * 获取文档列表（GET /api/documents）
     * - 返回当前用户的所有文档列表
     * - 权限：@Authenticated，需要 token
     * - 详解：从 SecurityContextHolder 取当前用户 ID，调用 Service.listByUser
     * - 为什么这样写：列表只显示自己的文档，防止泄露别人数据；用 GET 无参数简单
     */
    @GetMapping
    public List<DocumentEntity> getDocuments() {
        // 从 SecurityContextHolder 取当前用户 ID
        String username = SecurityUtil.getCurrentUsername();
        log.info("当前登录用户: {}", username);  // 获取当前登录用户名

        List<DocumentEntity> list = documentService.listByUser(username);
        log.info("查询到文档数量: {}", list.size());

        return list;
    }

    /**
     * 获取单个文档详情（GET /api/documents/{id}）
     * - 根据 id 查找文档
     * - 加权限检查：只有 owner 才能查看（否则 403）
     * - 权限：@Authenticated，需要 token
     * - 详解：先调用 Service.getById 找文档，然后检查 ownerId == 当前用户 ID
     * - 为什么这样写：防止用户访问别人文档；用 Optional 处理不存在情况，返回 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentEntity> getDocumentById(@PathVariable String id) {
        //1. 通过id查询文档
        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        // 2. 检查文档是否存在
        if (optionalDoc.isEmpty()) { //optional作为一个容器，里面装着数据，如果数据不存在，则返回null
            return ResponseEntity.notFound().build();  // 404 Not Found
        }
        // 3. 获取当前用户 ID
        DocumentEntity doc = optionalDoc.get();
        String currentUserId = SecurityUtil.getCurrentUsername(); // 从 SecurityContextHolder 取当前用户 ID
        // 4. 检查 ownerId 是否匹配
        if (!doc.getOwnerId().equals(currentUserId)) {
            return ResponseEntity.status(403).build();  // 403 Forbidden（不是 owner）
        }

        return ResponseEntity.ok(doc);  // 200 OK + 文档 JSON
    }

    /**
     * 搜索文档（GET /api/documents/search）
     * - 支持按标题模糊搜索（ignore case）
     * - 只返回当前用户的文档
     * - 支持分页（可选）
     * - 权限：@Authenticated，需要 token
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
     * 删除文档（DELETE /api/documents/{id}）
     * - 根据 id 删除文档
     * - 加权限检查：只有 owner 才能删除（否则 403）
     * - 权限：@Authenticated，需要 token
     * - 详解：先查文档，检查 ownerId == 当前用户 ID，然后调用 delete
     * - 为什么这样写：防止删除别人文档；返回字符串消息供前端 toast
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable String id) {
        String username = SecurityUtil.getCurrentUsername();
        log.info("删除文档请求 - ID: {}, 用户: {}", id, username);

        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        if (optionalDoc.isEmpty()) {
            return ResponseEntity.notFound().build();  // 404 Not Found
        }

        DocumentEntity doc = optionalDoc.get();
        if (!doc.getOwnerId().equals(username)) {
            return ResponseEntity.status(403).body("无权限删除此文档");  // 403 Forbidden
        }

        documentService.deleteById(id);  // 调用 Repository 删除
        log.info("删除成功 - ID: {}", id);

        return ResponseEntity.ok("删除成功");
    }

    /**
     * 更新文档内容（PUT /api/documents/{id}）
     * - 接收新的 content
     * - 加权限检查：只有 owner 才能修改
     * - 权限：@Authenticated，需要 token
     * - 详解：先查文档，检查 ownerId，再更新 content + version 自动递增
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentEntity> updateDocument(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String username = SecurityUtil.getCurrentUsername();
        log.info("更新文档 - ID: {}, 用户: {}", id, username);

        Optional<DocumentEntity> optionalDoc = documentService.getById(id);
        if (optionalDoc.isEmpty()) {
            return ResponseEntity.notFound().build();  // 404
        }
        DocumentEntity doc = optionalDoc.get();
        if (!doc.getOwnerId().equals(username)) {
            return ResponseEntity.status(403).body(null);  // 403 Forbidden
        }
        String newContent = request.get("content");
        String newTitle = request.get("title");
        if (newContent == null) {
            return ResponseEntity.badRequest().body(null);  // 400
        }
        doc.setContent(newContent);
        doc.setTitle(newTitle);
        // version 由 @Version 自动递增
        DocumentEntity updated = documentRepository.save(doc);
        return ResponseEntity.ok(updated);
    }
}