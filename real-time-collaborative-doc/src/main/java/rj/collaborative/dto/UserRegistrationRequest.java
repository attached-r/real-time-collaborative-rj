package rj.collaborative.dto;

import lombok.Data;
/**
 * 用户注册请求参数 传输对象
 */
@Data
public class UserRegistrationRequest {
    private String username;
    private String password;
    private String email;
}