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
        String password = "123456";

        // 管理员
        if (createOrUpdateUser("admin", "13068735578", "4fYga@PjXkDek.h7v", "系统管理员", null, "ADMIN", password, "admin")) {
            created++;
        } else {
            updated++;
        }

        // 创作者（北京大学）
        if (createOrUpdateUser("creator", "13068735577", "gCyJvX@Ct2SKWG.V2W", "认证创作者", "3", "CREATOR", password, "creator")) {
            created++;
        } else {
            updated++;
        }

        // 普通用户（北京大学）
        if (createOrUpdateUser("normaluser", "13068735576", "q6nAwHkBC@zNO7.GOx", "普通测试用户", "3", "USER", password, "normaluser")) {
            created++;
        } else {
            updated++;
        }

        String msg = String.format("初始化完成：新建%d个账号，更新%d个账号。\n管理员：13068735578/%s\n创作者：13068735577/%s\n普通用户：13068735576/%s",
                created, updated, password, password, password);
        return Result.success(msg, null);
    }

    @PostMapping("/set-creator")
    public Result<String> setCreator(@RequestParam String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setRole("CREATOR");
        userMapper.updateById(user);
        return Result.success(String.format("已将用户 %s（%s）设置为创作者", user.getUsername(), phone), null);
    }

    private boolean createOrUpdateUser(String username, String phone, String email, String bio, String schoolId, String role, String password, String avatarSeed) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (existing == null) {
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setUsername(username);
            user.setPhone(phone);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setBio(bio);
            user.setSchoolId(schoolId);
            user.setRole(role);
            user.setStatus(1);
            user.setDeleted(false);
            user.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=" + avatarSeed);
            userMapper.insert(user);
            return true;
        } else {
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setRole(role);
            existing.setStatus(1);
            existing.setDeleted(false);
            if (schoolId != null && existing.getSchoolId() == null) {
                existing.setSchoolId(schoolId);
            }
            userMapper.updateById(existing);
            return false;
        }
    }
}
