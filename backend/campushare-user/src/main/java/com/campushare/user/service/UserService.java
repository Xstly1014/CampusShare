package com.campushare.user.service;

import com.campushare.user.dto.*;
import com.campushare.user.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 用户注册
     */
    LoginResponse register(RegisterRequest request);
    
    /**
     * 发送验证码
     */
    void sendVerifyCode(String account, String type);
    
    /**
     * 根据ID获取用户信息
     */
    User getUserById(String userId);
    
    /**
     * 获取当前用户信息
     */
    UserDTO getCurrentUser(String userId);
    
    /**
     * 重置密码（忘记密码）
     */
    void resetPassword(ResetPasswordRequest request);
}