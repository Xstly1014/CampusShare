package com.campushare.user.service;

import com.campushare.user.dto.*;
import com.campushare.user.entity.User;

import java.util.List;

public interface UserService {
    
    LoginResponse login(LoginRequest request);
    
    LoginResponse register(RegisterRequest request);
    
    void sendVerifyCode(String account, String type);
    
    User getUserById(String userId);
    
    UserDTO getCurrentUser(String userId);

    UserDTO updateProfile(String userId, UpdateProfileRequest request);

    UserDTO updatePrivacy(String userId, UpdatePrivacyRequest request);

    UserDTO updateNotificationSettings(String userId, UpdateNotificationSettingsRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    UserDTO bindEmail(String userId, ChangeAccountRequest request);

    UserDTO bindPhone(String userId, ChangeAccountRequest request);

    void realNameVerify(String userId, String realName, String idCard);

    void resetPassword(ResetPasswordRequest request);

    List<User> searchUsers(String keyword, String excludeUserId);
}