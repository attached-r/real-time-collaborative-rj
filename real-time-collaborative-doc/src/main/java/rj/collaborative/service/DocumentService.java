package rj.collaborative.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;
import rj.collaborative.utils.SecurityUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    @Autowired  //注入实体类
    private DocumentRepository documentRepository;

    /**
     * 创建文档（设置 owner 为当前登录用户 ID）
     */
    public DocumentEntity create(String title, String content) {
        // 1. 安全检查：必须登录
        if (!SecurityUtil.isAuthenticated()) {
            throw new IllegalStateException("请先登录");
        }
        // 2. 获取当前用户名作为 ownerId（当前阶段用 username，后期可改成 getCurrentUserId()）
        String ownerId = SecurityUtil.getCurrentUsername();
        // 3. 构建文档实体
        DocumentEntity doc = DocumentEntity.builder()
                .title(title)
                .content(content)
                .ownerId(ownerId)           // 绑定创建者
                .version(null)              // 新文档 version 留 null，避免乐观锁冲突
                .versions(new ArrayList<>()) // 初始化空历史列表
                .build();
        // 4. 保存并返回（MongoDB 自动生成 id）
        return documentRepository.save(doc);
    }

    /**
     * 根据 ID 获取文档  通过 Spring Data JPA 的 CrudRepository 自动生成的
     */
    public Optional<DocumentEntity> getById(String id) {
        return documentRepository.findById(id);
    }

    /**
     * 根据用户 ID 列出所有文档 自定义查询
     */
    public List<DocumentEntity> listByUser(String userId) {
        return documentRepository.findByOwnerId(userId);
    }
}