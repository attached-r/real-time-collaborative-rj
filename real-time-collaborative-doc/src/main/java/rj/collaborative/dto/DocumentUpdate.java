// 在 rj.collaborative.dto 包中创建 DocumentUpdate.java
package rj.collaborative.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentUpdate {
    private String content;
    private Long version;
}
