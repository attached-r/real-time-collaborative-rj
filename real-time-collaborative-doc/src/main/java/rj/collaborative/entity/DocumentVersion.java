package rj.collaborative.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档版本实体类
 * 用于存储文档的版本历史信息
 */
@Data
@Builder
@AllArgsConstructor
public class DocumentVersion {
    private Long versionNumber;
    private String editorId;  // 编辑者 ID
    private String delta;     // Quill Delta JSON 或 patch 字符串
    private LocalDateTime timestamp;


    public DocumentVersion() {
        this.timestamp = LocalDateTime.now();
    }
}
