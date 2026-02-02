package rj.collaborative.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing  // 启用 MongoDB 审计功能
public class MongoDBConfig {

}
