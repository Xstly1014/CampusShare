package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.BindAccountRequest;
import com.campushare.user.dto.ChangePasswordRequest;
import com.campushare.user.dto.UpdateProfileRequest;
import com.campushare.user.dto.UserDTO;
import com.campushare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    /**
     * 更新当前用户资料（昵称、简介、头像）
     */
    @PutMapping("/me")
    public Result<UserDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody UpdateProfileRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.updateProfile(userId, request);
        return Result.success(user);
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangePasswordRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        userService.changePassword(userId, request);
        return Result.success("密码修改成功", null);
    }

    @PutMapping("/me/email")
    public Result<UserDTO> bindEmail(
            @RequestHeader("Authorization") String token,
            @RequestBody BindAccountRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.bindEmail(userId, request);
        return Result.success(user);
    }

    @PutMapping("/me/phone")
    public Result<UserDTO> bindPhone(
            @RequestHeader("Authorization") String token,
            @RequestBody BindAccountRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        UserDTO user = userService.bindPhone(userId, request);
        return Result.success(user);
    }
}