package rj.collaborative.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rj.collaborative.entity.DocumentEntity;
import rj.collaborative.repository.DocumentRepository;

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
        // 从 SecurityContextHolder 取当前用户 ID（JWT 认证后可用）
        String ownerId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();  // 假设 token 里存 ID，后期调整

        DocumentEntity doc = DocumentEntity.builder()
                .title(title)
                .content(content)
                .ownerId(ownerId)
                .version(0L)
                .versions(new ArrayList<>())
                .build();

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