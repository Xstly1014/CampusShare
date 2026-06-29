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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return Result.success("注册成功", response);
    }

    @PostMapping("/send-code")
    public Result<Void> sendVerifyCode(@RequestParam String account,
                                      @RequestParam(defaultValue = "phone") String type) {
        userService.sendVerifyCode(account, type);
        return Result.success("验证码发送成功", null);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(request);
        return Result.success("密码重置成功", null);
    }

    @PostMapping("/init-default-users")
    public Result<String> initDefaultUsers() {
        int created = 0;
        int updated = 0;

        String adminPhone = "13068735578";
        String testPhone = "13068735577";
        String password = "123456";

        // 删除可能存在的错误字母手机号账号，避免冲突
        userMapper.delete(new LambdaQueryWrapper<User>().in(User::getPhone, "NOkT4YYwjyD", "tlZmmRD4L1f"));

        // 管理员
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, adminPhone));
        if (admin == null) {
            admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername("admin");
            admin.setPhone(adminPhone);
            admin.setEmail("4fYga@PjXkDek.h7v");
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setBio("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setDeleted(false);
            admin.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=admin");
            userMapper.insert(admin);
            created++;
        } else {
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setDeleted(false);
            userMapper.updateById(admin);
            updated++;
        }

        // 普通用户（默认北京大学）
        User testuser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, testPhone));
        if (testuser == null) {
            testuser = new User();
            testuser.setId(UUID.randomUUID().toString());
            testuser.setUsername("testuser");
            testuser.setPhone(testPhone);
            testuser.setEmail("yDIk@oz12GUV.ROY");
            testuser.setPasswordHash(passwordEncoder.encode(password));
            testuser.setBio("测试用户");
            testuser.setSchoolId("3");
            testuser.setRole("USER");
            testuser.setStatus(1);
            testuser.setDeleted(false);
            testuser.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=testuser");
            userMapper.insert(testuser);
            created++;
        } else {
            testuser.setPasswordHash(passwordEncoder.encode(password));
            testuser.setRole("USER");
            testuser.setStatus(1);
            testuser.setDeleted(false);
            if (testuser.getSchoolId() == null) {
                testuser.setSchoolId("3");
            }
            userMapper.updateById(testuser);
            updated++;
        }

        String msg = String.format("初始化完成：新建%d个账号，更新%d个账号。管理员：%s/%s，测试用户：%s/%s",
                created, updated, adminPhone, password, testPhone, password);
        return Result.success(msg, null);
    }
}
