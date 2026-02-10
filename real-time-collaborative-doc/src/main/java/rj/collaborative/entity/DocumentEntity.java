package rj.collaborative.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

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
    @JsonSerialize(using = ToStringSerializer.class) // 关键：强制将 ObjectId 序列化为字符串
    private String id; // 或者 private ObjectId id

    private String title;  // 文档标题

    private org.bson.Document content;  // 使用 BSON Document 类型 content

    private String ownerId;  // 创建者用户 ID (User.id) 也可以用名字作为id

    // 新增：协作者列表（用户名）
    private List<String> collaborators = new ArrayList<>();

    @Version  // 乐观锁字段，自动递增，防止并发覆盖
    private Long version ;

    // 版本历史：嵌入式 List（每个元素存 delta 变更）
    private List<DocumentVersion> versions = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

