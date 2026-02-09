package rj.collaborative.service;

import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;
import rj.collaborative.utils.SecurityUtil;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.*;
@Slf4j
@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * 创建文档（设置 owner 为当前登录用户 ID）
     */
    public DocumentEntity create(String title, String content) {
        if (!SecurityUtil.isAuthenticated()) {
            throw new IllegalStateException("请先登录");
        }
        String ownerId = SecurityUtil.getCurrentUsername();

        DocumentEntity doc = DocumentEntity.builder()
                .title(title)
                .content(content)
                .ownerId(ownerId)
                .version(null)
                .versions(new ArrayList<>())
                .collaborators(new ArrayList<>())   // 新增：初始化协作者列表
                .build();

        return documentRepository.save(doc);
    }

    /**
     * 根据 ID 获取文档（新增：支持协作者访问）
     */
    public Optional<DocumentEntity> getById(String id) {
        Optional<DocumentEntity> optionalDoc = documentRepository.findById(id);
        if (optionalDoc.isPresent()) {
            checkAccess(optionalDoc.get());   // 新增权限检查
        }
        return optionalDoc;
    }

    /**
     * 根据用户 ID 列出所有文档（新增：支持协作者文档也显示）
     */
    public List<DocumentEntity> listByUser(String userId) {
        // 原有查询 + 协作者文档（可优化为一条查询，这里保持简单）
        List<DocumentEntity> owned = documentRepository.findByOwnerId(userId);
        List<DocumentEntity> collaborated = documentRepository.findByCollaboratorsContaining(userId);
        owned.addAll(collaborated);
        return owned;
    }

    /**
     * 搜索文档（新增：支持协作者文档搜索）
     */
    public List<DocumentEntity> searchByUserAndKeyword(String username, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (StringUtils.isBlank(keyword)) {
            return documentRepository.findByOwnerId(username, pageable).getContent();
        }

        return documentRepository.findByOwnerIdAndTitleContainingIgnoreCase(
                username, keyword.trim(), pageable).getContent();
    }

    /**
     * 根据ID删除文档
     */
    public void deleteById(String id) {
        documentRepository.deleteById(id);
    }

    //--------------------------------------
    //新增：获取当前用户的所有可访问文档（我的+被分享的）
    //---------------------------------------
    /**
     * 获取当前用户所有可访问文档（我的 + 被分享的）
     */
    public List<DocumentEntity> getAccessibleDocuments(String username) {
        // 我的文档
        List<DocumentEntity> owned = documentRepository.findByOwnerId(username);
        // 被分享给我的文档
        List<DocumentEntity> shared = documentRepository.findByCollaboratorsContaining(username);

        // 合并去重
        Set<DocumentEntity> all = new HashSet<>(owned);
        all.addAll(shared);

        // 按更新时间降序
        return all.stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    // ────────────────────────────────────────────────
    // 新增：分享功能
    // ────────────────────────────────────────────────

    /**
     * 分享文档给其他用户（加到 collaborators）
     */
    public void shareWithUser(String docId, String targetUsername) {
        String currentUser = SecurityUtil.getCurrentUsername();

        Optional<DocumentEntity> optionalDoc = documentRepository.findById(docId);
        if (optionalDoc.isEmpty()) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        DocumentEntity doc = optionalDoc.get();

        // 只有 owner 可以分享
        if (!doc.getOwnerId().equals(currentUser)) {
            throw new SecurityException("只有文档拥有者可以分享");
        }

        if (doc.getCollaborators().contains(targetUsername)) {
            throw new IllegalArgumentException("该用户已是协作者");
        }

        doc.getCollaborators().add(targetUsername);
        documentRepository.save(doc);
    }

    // ────────────────────────────────────────────────
    // 新增：统一权限检查方法（最少改动原有代码）
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
    // 实时协作相关方法（你已有的，保持不变）
    // ────────────────────────────────────────────────
    /**
     * 应用 Delta 更新文档内容
     * @param docId 文档 ID
     * @param delta 前端传来的 Delta JSON
     * @param headerAccessor STOMP 消息头（从中取用户名）
     */
    public String applyDelta(String docId, String delta, SimpMessageHeaderAccessor headerAccessor) {
        String currentUser = (String) headerAccessor.getSessionAttributes().get("username");
        if (currentUser == null) {
            log.error("WebSocket 编辑请求未携带用户名，docId: {}", docId);
            throw new IllegalStateException("WebSocket 未认证，请重新连接");
        }

        log.info("收到增量编辑 - 用户: {}, 文档ID: {}, 增量长度: {}",
                currentUser, docId, delta.length());

        Optional<DocumentEntity> optionalDoc = documentRepository.findById(docId);
        if (optionalDoc.isEmpty()) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        DocumentEntity doc = optionalDoc.get();

        // 权限检查
        if (!doc.getOwnerId().equals(currentUser) &&
                !doc.getCollaborators().contains(currentUser)) {
            throw new SecurityException("无权限编辑此文档");
        }

        String currentContent = doc.getContent() != null ? doc.getContent() : "";
        String updatedContent = currentContent + delta;  // 直接追加增量

        try {
            doc.setContent(updatedContent);
//            documentRepository.save(doc);
            log.info("保存成功，文档ID: {}, 新内容长度: {}", docId, updatedContent.length());
        } catch (OptimisticLockingFailureException e) {
            log.warn("乐观锁冲突，使用最新版本追加 - docId: {}", docId);
            doc = documentRepository.findById(docId).orElse(doc);
            updatedContent = (doc.getContent() != null ? doc.getContent() : "") + delta;
        } catch (Exception e) {
            log.error("保存异常，但继续广播内存内容", e);
        }

        // 返回完整字符串（前端直接覆盖）
        return updatedContent;
    }


    public String getCurrentContent(String docId) {
        Optional<DocumentEntity> doc = documentRepository.findById(docId);
        return doc.map(DocumentEntity::getContent).orElse("");
    }
}