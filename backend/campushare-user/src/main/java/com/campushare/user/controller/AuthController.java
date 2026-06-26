package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.user.dto.*;
import com.campushare.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return Result.success("注册成功", response);
    }
    
    /**
     * 发送验证码
     */
    @PostMapping("/send-code")
    public Result<Void> sendVerifyCode(@RequestParam String account, 
                                      @RequestParam(defaultValue = "phone") String type) {
        userService.sendVerifyCode(account, type);
        return Result.success("验证码发送成功", null);
    }
}