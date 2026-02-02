package rj.collaborative.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import rj.collaborative.entity.User;
import rj.collaborative.service.UserService;

import java.util.ArrayList;
import java.util.List;

@RestController
public class TestMongoController {
    /**
    * 测试 MongoDB 连接
    * */
    @Autowired
    private MongoTemplate mongoTemplate;
    @GetMapping("/test-mongo")
    public String testConnection() {
        try {
            // 获取当前数据库名（应该返回 "editor" 或你配置的 db 名）
            String dbName = mongoTemplate.getDb().getName();

            // 正确获取集合列表 - 使用 toArray() 方法
            List<String> collections = mongoTemplate.getDb().listCollectionNames().into(new ArrayList<>());

            // 打印到控制台
            System.out.println("Connected to DB: " + dbName);
            System.out.println("Collections: " + collections);

            return "MongoDB 连接成功！\n" +
                    "数据库名: " + dbName + "\n" +
                    "集合列表: " + collections;
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }
    /**
     * 测试用户注册
     * */
    @Autowired
    private UserService userService;
    @GetMapping("/test-register")
    public String testRegister() {
        try {
            User user = userService.register(
                    "testuser001",      // username
                    "123456",           // 密码（明文，Service 里会加密）
                    "test001@example.com"
            );
            return "注册成功！用户ID: " + user.getId() +
                    ", 用户名: " + user.getUsername() +
                    ", 创建时间: " + user.getCreatedAt();
        } catch (RuntimeException e) {
            return "注册失败：" + e.getMessage();
        }
    }
}
