package rj.collaborative.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import rj.collaborative.entity.DocumentEntity;

import java.util.List;

public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {

    // 自定义查询：按 ownerId 查找所有文档
    List<DocumentEntity> findByOwnerId(String ownerId);

    // 分页查询当前用户所有文档
    Page<DocumentEntity> findByOwnerId(String ownerId, Pageable pageable);

    // 分页模糊搜索标题（忽略大小写）
    Page<DocumentEntity> findByOwnerIdAndTitleContainingIgnoreCase(
            String ownerId, String title, Pageable pageable);
}
