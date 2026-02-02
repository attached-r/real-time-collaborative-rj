package rj.collaborative.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rj.collaborative.security.JwtTokenProvider;
import rj.collaborative.service.UserService;

import java.util.Map;
/**
 * 该控制器是用来处理 用户注册和登录 请求
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired //处理用户认证的核心组件
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");

        userService.register(username, password, email);  //调用注册方法
        return ResponseEntity.ok("注册成功");  //返回注册成功信息
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");  //登录只需要用户名密码

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("用户名或密码不能为空");
        }

        // 调用 AuthenticationManager.authenticate() 方法进行认，证触发校验（查数据库、比密码）
        //Security 会：
        //触发 UserDetailsService - 自动调用您实现的 CustomUserDetailsService.loadUserByUsername()
        //数据库查询 - 通过 userRepository.findByUsername(username) 查询用户
        //密码比对 - 自动使用 PasswordEncoder 比对输入密码和数据库中的加密密码
        //认证决策 - 根据查询结果和密码比对决定认证是否成功
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        // 把“登录成功”的状态记下来，让整个请求都知道“我已经登录了”。
        // 设置 SecurityContextHolder->"全局保险箱”，每个请求线程都有一个独立的上下文。
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 生成 JWT token，用于后续请求的认证
        String token = jwtTokenProvider.generateToken(authentication);

        // 返回给前端JWT token，以json格式
        return ResponseEntity.ok(Map.of("token", token));
    }
}
/** 代码解释：
 * 1.ResponseEntity 是 Spring Framework 提供的一个核心类，属于 org.springframework.http 包。
 * 主要用途：
 * HTTP响应封装 - 封装HTTP响应的状态码、头部和body
 * RESTful API标准 - 构建符合REST规范的响应
 * 灵活的状态控制 - 可以自定义HTTP状态码
 *
 * 2.
 *Spring Security 在这里的作用（简单理解）
 * Spring Security 的认证流程大概是：
 *
 * 用户发 POST /login → AuthController 接收用户名密码
 * authenticationManager.authenticate() → 触发校验（查数据库、比密码）
 * 校验成功 → 得到 Authentication 对象（已认证状态）
 * 把这个状态存到 SecurityContextHolder（线程安全）
 * 生成 JWT token（自定义）
 * 返回 token 给前端
 *
 * 后续请求：
 *
 * 浏览器/Postman 带 token → JwtAuthenticationFilter 拦截
 * Filter 解析 token → 校验签名 + 过期时间 → 从 token 取用户名
 * 调用 userDetailsService.loadUserByUsername 加载用户详情
 * 重新构建 Authentication → 存到 SecurityContextHolder
 * 控制器就能知道“我是谁、有啥权限”
 */