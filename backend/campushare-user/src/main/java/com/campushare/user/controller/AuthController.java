package com.campushare.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.common.result.Result;
import com.campushare.user.dto.*;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    
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
    
    /**
     * 重置密码（忘记密码）
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(request);
        return Result.success("密码重置成功", null);
    }

    /**
     * 初始化默认账号（管理员+测试用户）—— 仅用于初始化环境，可重复调用
     */
    @PostMapping("/init-default-users")
    public Result<String> initDefaultUsers() {
        int created = 0;
        int updated = 0;

        // 管理员：NOkT4YYwjyD / 123456
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, "NOkT4YYwjyD"));
        if (admin == null) {
            admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername("admin");
            admin.setPhone("NOkT4YYwjyD");
            admin.setEmail("4fYga@PjXkDek.h7v");
            admin.setPasswordHash(passwordEncoder.encode("123456"));
            admin.setBio("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setDeleted(false);
            admin.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=admin");
            userMapper.insert(admin);
            created++;
        } else {
            admin.setPasswordHash(passwordEncoder.encode("123456"));
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setDeleted(false);
            userMapper.updateById(admin);
            updated++;
        }

        // 普通用户：tlZmmRD4L1f / 123456（默认北京大学）
        User testuser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, "tlZmmRD4L1f"));
        if (testuser == null) {
            testuser = new User();
            testuser.setId(UUID.randomUUID().toString());
            testuser.setUsername("testuser");
            testuser.setPhone("tlZmmRD4L1f");
            testuser.setEmail("yDIk@oz12GUV.ROY");
            testuser.setPasswordHash(passwordEncoder.encode("123456"));
            testuser.setBio("测试用户");
            testuser.setSchoolId("3");
            testuser.setRole("USER");
            testuser.setStatus(1);
            testuser.setDeleted(false);
            testuser.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=testuser");
            userMapper.insert(testuser);
            created++;
        } else {
            testuser.setPasswordHash(passwordEncoder.encode("123456"));
            testuser.setRole("USER");
            testuser.setStatus(1);
            testuser.setDeleted(false);
            if (testuser.getSchoolId() == null) {
                testuser.setSchoolId("3");
            }
            userMapper.updateById(testuser);
            updated++;
        }

        String msg = String.format("初始化完成：新建%d个账号，更新%d个账号。管理员：NOkT4YYwjyD/123456，测试用户：tlZmmRD4L1f/123456", created, updated);
        return Result.success(msg, null);
    }
}