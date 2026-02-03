package rj.collaborative.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rj.collaborative.entity.DocumentEntity;

import java.util.List;

public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {

    // 自定义查询：按 ownerId 查找所有文档
    List<DocumentEntity> findByOwnerId(String ownerId);
}