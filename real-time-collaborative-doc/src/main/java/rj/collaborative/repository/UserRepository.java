package rj.collaborative.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rj.collaborative.entity.User;

import java.util.Optional;  //  避免值为null

// 数据访问层 继承 MongoRepository 接口
public interface UserRepository extends MongoRepository<User, String> {
    // 自定义查询方法

    // 按 username 查找（Spring Data MongoDB 自动实现）
    Optional<User> findByUsername(String username);
}

/**
 *核心概念说明：
 *1. 继承 MongoRepository
 * MongoRepository<User, String> 是 Spring Data MongoDB 提供的接口
 * 第一个泛型参数 User 表示操作的实体类
 * 第二个泛型参数 String 表示主键的数据类型
 * 2. 自动实现的 CRUD 方法
 * 继承 MongoRepository 后，Spring Data 会自动提供以下方法：
 * save(User user) - 保存或更新用户
 * findById(String id) - 根据 ID 查找用户
 * findAll() - 获取所有用户
 * deleteById(String id) - 根据 ID 删除用户
 * count() - 统计用户数量
 **/