package rj.collaborative.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documents")  // MongoDB 集合名：documents
public class DocumentEntity {

    @Id
    private String id;  // ObjectId 作为 String

    private String title;  // 文档标题

    private String content;  // 内容（富文本 JSON 字符串或 HTML）

    private String ownerId;  // 创建者用户 ID (User.id) 也可以用名字作为id

    @Version  // 乐观锁字段，自动递增，防止并发覆盖
    private Long version = 0L;  // 从 0 开始

    // 版本历史：嵌入式 List（每个元素存 delta 变更）
    private List<DocumentVersion> versions = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

