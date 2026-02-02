package rj.collaborative.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")  // MongoDB 集合名：users
public class User {  // 用户实体类
    @Id
    private String id;  // MongoDB ObjectId 作为 String

    @Indexed(unique = true)  // 用户名唯一索引，防止重复
    private String username;

    private String password;  // 加密后的密码（BCrypt）

    private String email;  // 可选字段，未来可加

    @CreatedDate  // 创建时间
    private LocalDateTime createdAt;

    @LastModifiedDate  // 自动更新时间
    private LocalDateTime updatedAt;
}

/**
 * @Builder 注解是 Lombok 提供的一个非常实用的注解，它的主要作用是：
 * 核心功能
 * 自动生成建造者模式代码 - 为类生成流畅的 Builder API
 * 简化对象创建 - 避免编写冗长的构造函数和 setter 方法
 *
 * // 使用 Builder 模式创建 User 对象
 * User user = User.builder()
 *     .username("john_doe")
 *     .password("encrypted_password")
 *     .email("john@example.com")
 *     .build();
 */