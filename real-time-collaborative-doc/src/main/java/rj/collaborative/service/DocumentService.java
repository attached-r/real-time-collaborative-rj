package rj.collaborative.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;  // BSON 类型
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;
import rj.collaborative.utils.SecurityUtil;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    // ────────────────────────────────────────────────
    // 创建文档（最关键：content 初始化为 BSON Document）
    // ────────────────────────────────────────────────
    /**
     * 创建文档（最关键修复）
     * - content 统一包装为 Document{"text": "..."}
     * - id 由 MongoDB 自动生成（不手动设置）
     */
    public DocumentEntity create(String title, String initialContent) {
        if (!SecurityUtil.isAuthenticated()) {
            throw new IllegalStateException("请先登录");
        }
        String ownerId = SecurityUtil.getCurrentUsername();

        // 【改动点】正确初始化 BSON Document
        Document contentDoc = new Document("text", initialContent != null ? initialContent : "");

        DocumentEntity doc = DocumentEntity.builder()
                .title(title)
                .content(contentDoc)
                .ownerId(ownerId)
                //.version(0L)
                .versions(new ArrayList<>())
                .collaborators(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DocumentEntity saved = documentRepository.save(doc);
        log.info("创建文档成功 - ID: {}, owner: {}", saved.getId().toString(), ownerId);
        return saved;
    }

    // ────────────────────────────────────────────────
    // 获取单个文档（已支持协作者）
    // ────────────────────────────────────────────────
    /**
     * 获取单个文档（支持 String id → ObjectId 自动转换）
     */
    public Optional<DocumentEntity> getById(String id) {
        Optional<DocumentEntity> optionalDoc = documentRepository.findById(id);
        if (optionalDoc.isPresent()) {
            checkAccess(optionalDoc.get());
        }
        return optionalDoc;
    }
    // ────────────────────────────────────────────────
    // 列出用户所有文档（我的 + 协作者的）
    // ────────────────────────────────────────────────
    public List<DocumentEntity> listByUser(String userId) {
        List<DocumentEntity> owned = documentRepository.findByOwnerId(userId);
        List<DocumentEntity> collaborated = documentRepository.findByCollaboratorsContaining(userId);
        owned.addAll(collaborated);
        return owned;
    }

    // ────────────────────────────────────────────────
    // 搜索文档
    // ────────────────────────────────────────────────
    public List<DocumentEntity> searchByUserAndKeyword(String username, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (StringUtils.isBlank(keyword)) {
            return documentRepository.findByOwnerId(username, pageable).getContent();
        }
        return documentRepository.findByOwnerIdAndTitleContainingIgnoreCase(
                username, keyword.trim(), pageable).getContent();
    }

    // ────────────────────────────────────────────────
    // 删除文档
    // ────────────────────────────────────────────────
    public void deleteById(String id) {
        documentRepository.deleteById(id);
    }

    // ────────────────────────────────────────────────
    // 获取所有可访问文档（我的 + 被分享的）
    // ────────────────────────────────────────────────
    public List<DocumentEntity> getAccessibleDocuments(String username) {
        List<DocumentEntity> owned = documentRepository.findByOwnerId(username);
        List<DocumentEntity> shared = documentRepository.findByCollaboratorsContaining(username);

        Set<DocumentEntity> all = new HashSet<>(owned);
        all.addAll(shared);

        return all.stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    // ────────────────────────────────────────────────
    // 分享文档
    // ────────────────────────────────────────────────
    public void shareWithUser(String docId, String targetUsername) {
        String currentUser = SecurityUtil.getCurrentUsername();
        Optional<DocumentEntity> opt = documentRepository.findById(docId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("文档不存在");
        }

        DocumentEntity doc = opt.get();
        if (!doc.getOwnerId().equals(currentUser)) {
            throw new SecurityException("只有拥有者可以分享");
        }

        if (doc.getCollaborators().contains(targetUsername)) {
            throw new IllegalArgumentException("该用户已是协作者");
        }

        doc.getCollaborators().add(targetUsername);
        documentRepository.save(doc);
    }

    // ────────────────────────────────────────────────
    // 统一权限检查
    // ────────────────────────────────────────────────
    public void checkAccess(DocumentEntity doc) {
        String currentUser = SecurityUtil.getCurrentUsername();
        if (currentUser == null) {
            throw new IllegalStateException("请先登录");
        }
        if (!doc.getOwnerId().equals(currentUser) &&
                !doc.getCollaborators().contains(currentUser)) {
            throw new SecurityException("无权限访问此文档");
        }
    }

    // ────────────────────────────────────────────────
    // 实时协作：应用 delta（追加到 content.text）
    // ────────────────────────────────────────────────
    public String applyDelta(String docId, String newText, SimpMessageHeaderAccessor headerAccessor) {
        String currentUser = (String) headerAccessor.getSessionAttributes().get("username");
        if (currentUser == null) throw new IllegalStateException("WebSocket 未认证");

        Optional<DocumentEntity> opt = documentRepository.findById(docId);
        if (opt.isEmpty()) throw new IllegalArgumentException("文档不存在");

        DocumentEntity doc = opt.get();
        checkAccess(doc);

        try {
            doc.setContent(new Document("text", newText));
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
            log.info("实时更新成功 - docId:{}, 用户:{}, 新长度:{}", docId, currentUser, newText.length());
        } catch (Exception e) {
            log.error("保存实时内容失败", e);
            throw e;
        }

        return newText;
    }

    // ────────────────────────────────────────────────
    // 获取当前纯文本内容（供前端初始化）
    // ────────────────────────────────────────────────
    public String getCurrentContent(String docId) {
        Optional<DocumentEntity> opt = documentRepository.findById(docId);
        if (opt.isEmpty()) {
            return "";
        }
        Document contentDoc = opt.get().getContent();
        return (contentDoc != null && contentDoc.containsKey("text"))
                ? contentDoc.getString("text")
                : "";
    }

}