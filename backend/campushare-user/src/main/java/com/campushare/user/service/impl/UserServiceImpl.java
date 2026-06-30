package com.campushare.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.common.constant.RedisConstants;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.*;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CreatorService;
import com.campushare.user.service.EmailService;
import com.campushare.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final CreatorService creatorService;
    
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = findByAccount(request.getAccount());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_LOGIN_ERROR);
        }
        
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN);
        }
        
        String token = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        
        saveTokenToRedis(user.getId(), token);
        
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getExpiration())
                .user(convertToUserDTO(user))
                .build();
    }
    
    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String key = RedisConstants.VERIFY_CODE_PREFIX + request.getAccount();
        String cachedCode = redisTemplate.opsForValue().get(key);
        log.info("验证验证码 - key: {}, 缓存验证码: {}, 用户输入: {}", key, cachedCode, request.getVerifyCode());
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }
        
        redisTemplate.delete(RedisConstants.VERIFY_CODE_PREFIX + request.getAccount());
        
        if (userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()))) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST, "用户名已存在");
        }
        
        if ("phone".equals(request.getRegisterType())) {
            if (userMapper.exists(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, request.getAccount()))) {
                throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST, "手机号已注册");
            }
        } else if ("email".equals(request.getRegisterType())) {
            if (userMapper.exists(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, request.getAccount()))) {
                throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST, "邮箱已注册");
            }
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .email("email".equals(request.getRegisterType()) ? request.getAccount() : null)
                .phone("phone".equals(request.getRegisterType()) ? request.getAccount() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .creatorLevel("NONE")
                .status(1)
                .publicPosts(true)
                .publicStars(false)
                .publicLikes(false)
                .publicHistory(false)
                .searchable(true)
                .notifyMessages(true)
                .notifyReplies(true)
                .notifyLikes(false)
                .build();

        userMapper.insert(user);

        user.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=" + user.getId());
        userMapper.updateById(user);
        
        String token = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        
        saveTokenToRedis(user.getId(), token);
        
        log.info("用户注册成功: {}", user.getUsername());
        
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getExpiration())
                .user(convertToUserDTO(user))
                .build();
    }
    
    @Override
    public void sendVerifyCode(String account, String type) {
        String countKey = RedisConstants.VERIFY_CODE_COUNT_PREFIX + account;
        String countStr = redisTemplate.opsForValue().get(countKey);
        int count = StrUtil.isBlank(countStr) ? 0 : Integer.parseInt(countStr);
        if (count >= RedisConstants.VERIFY_CODE_MAX_COUNT) {
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_TOO_FREQUENT);
        }

        String verifyCode = String.format("%06d", (int) ((Math.random() * 9 + 1) * 100000));

        redisTemplate.opsForValue().set(
            RedisConstants.VERIFY_CODE_PREFIX + account,
            verifyCode,
            RedisConstants.VERIFY_CODE_EXPIRE_TIME,
            TimeUnit.MILLISECONDS
        );

        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, 3600000, TimeUnit.MILLISECONDS);

        if ("email".equals(type) || account.contains("@")) {
            emailService.sendVerifyCodeEmail(account, verifyCode);
        } else {
            log.info("发送短信验证码到 {}: {}", account, verifyCode);
        }
    }
    
    @Override
    public User getUserById(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        return user;
    }
    
    @Override
    public UserDTO getCurrentUser(String userId) {
        User user = getUserById(userId);
        return convertToUserDTO(user);
    }

    @Override
    @Transactional
    public UserDTO updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            if (!request.getUsername().equals(user.getUsername())) {
                boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
                        .ne(User::getId, userId));
                if (exists) {
                    throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST);
                }
            }
            user.setUsername(request.getUsername().trim());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        userMapper.updateById(user);
        log.info("用户 {} 更新资料成功", userId);
        return convertToUserDTO(user);
    }

    @Override
    @Transactional
    public UserDTO updatePrivacy(String userId, UpdatePrivacyRequest request) {
        User user = getUserById(userId);
        if (request.getPublicPosts() != null) {
            user.setPublicPosts(request.getPublicPosts());
        }
        if (request.getPublicStars() != null) {
            user.setPublicStars(request.getPublicStars());
        }
        if (request.getPublicLikes() != null) {
            user.setPublicLikes(request.getPublicLikes());
        }
        if (request.getPublicHistory() != null) {
            user.setPublicHistory(request.getPublicHistory());
        }
        if (request.getSearchable() != null) {
            user.setSearchable(request.getSearchable());
        }
        userMapper.updateById(user);
        log.info("用户 {} 更新隐私设置成功", userId);
        return convertToUserDTO(user);
    }

    @Override
    @Transactional
    public UserDTO updateNotificationSettings(String userId, UpdateNotificationSettingsRequest request) {
        User user = getUserById(userId);
        if (request.getNotifyMessages() != null) {
            user.setNotifyMessages(request.getNotifyMessages());
        }
        if (request.getNotifyReplies() != null) {
            user.setNotifyReplies(request.getNotifyReplies());
        }
        if (request.getNotifyLikes() != null) {
            user.setNotifyLikes(request.getNotifyLikes());
        }
        userMapper.updateById(user);
        log.info("用户 {} 更新通知设置成功", userId);
        return convertToUserDTO(user);
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = getUserById(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_OLD_PASSWORD_ERROR);
        }
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(4001, "两次输入的密码不一致");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户 {} 修改密码成功", userId);
    }

    @Override
    @Transactional
    public UserDTO bindEmail(String userId, ChangeAccountRequest request) {
        User user = getUserById(userId);

        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            if (!Boolean.TRUE.equals(request.getRealNameVerify())) {
                if (request.getOriginalAccount() == null || request.getOriginalVerifyCode() == null) {
                    throw new BusinessException(4001, "换绑邮箱需先验证原邮箱");
                }
                if (!request.getOriginalAccount().equals(user.getEmail())) {
                    throw new BusinessException(4001, "原邮箱不匹配");
                }
                verifyCode(request.getOriginalAccount(), request.getOriginalVerifyCode());
            }
        }

        verifyCode(request.getNewAccount(), request.getNewVerifyCode());

        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getNewAccount())
                .ne(User::getId, userId));
        if (exists) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST);
        }
        user.setEmail(request.getNewAccount());
        userMapper.updateById(user);
        log.info("用户 {} 绑定/换绑邮箱 {}", userId, request.getNewAccount());
        return convertToUserDTO(user);
    }

    @Override
    @Transactional
    public UserDTO bindPhone(String userId, ChangeAccountRequest request) {
        User user = getUserById(userId);

        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            if (!Boolean.TRUE.equals(request.getRealNameVerify())) {
                if (request.getOriginalAccount() == null || request.getOriginalVerifyCode() == null) {
                    throw new BusinessException(4001, "换绑手机号需先验证原手机号");
                }
                if (!request.getOriginalAccount().equals(user.getPhone())) {
                    throw new BusinessException(4001, "原手机号不匹配");
                }
                verifyCode(request.getOriginalAccount(), request.getOriginalVerifyCode());
            }
        }

        verifyCode(request.getNewAccount(), request.getNewVerifyCode());

        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getNewAccount())
                .ne(User::getId, userId));
        if (exists) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_ALREADY_EXIST);
        }
        user.setPhone(request.getNewAccount());
        userMapper.updateById(user);
        log.info("用户 {} 绑定/换绑手机号 {}", userId, request.getNewAccount());
        return convertToUserDTO(user);
    }

    @Override
    public void realNameVerify(String userId, String realName, String idCard) {
        throw new BusinessException(4001, "实名认证功能暂未开放");
    }

    private void verifyCode(String account, String code) {
        String key = RedisConstants.VERIFY_CODE_PREFIX + account;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(code)) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }
        redisTemplate.delete(key);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String key = RedisConstants.VERIFY_CODE_PREFIX + request.getAccount();
        String cachedCode = redisTemplate.opsForValue().get(key);
        log.info("验证验证码 - key: {}, 缓存验证码: {}, 用户输入: {}", key, cachedCode, request.getVerifyCode());
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }
        
        User user = findByAccount(request.getAccount());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        
        redisTemplate.delete(RedisConstants.VERIFY_CODE_PREFIX + request.getAccount());
        
        log.info("用户重置密码成功: {}", user.getUsername());
    }

    @Override
    public List<User> searchUsers(String keyword, String excludeUserId) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getUsername, keyword)
                        .eq(User::getDeleted, false)
                        .ne(User::getId, excludeUserId)
                        .eq(User::getSearchable, true)
                        .last("LIMIT 20"));
    }
    
    private User findByAccount(String account) {
        log.info("尝试登录，账号: {}", account);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, account)
               .or()
               .eq(User::getUsername, account)
               .or()
               .eq(User::getEmail, account);
        User user = userMapper.selectOne(wrapper);
        if (user != null) {
            log.info("登录查询成功，用户: {}, phone: {}, role: {}", user.getUsername(), user.getPhone(), user.getRole());
        } else {
            log.warn("登录查询失败，未找到账号: {}", account);
        }
        return user;
    }
    
    private void saveTokenToRedis(String userId, String token) {
        String tokenKey = RedisConstants.USER_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(
            tokenKey,
            token,
            RedisConstants.USER_TOKEN_EXPIRE_TIME,
            TimeUnit.MILLISECONDS
        );
    }
    
    private UserDTO convertToUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .schoolId(user.getSchoolId())
                .createTime(user.getCreateTime() != null ?
                    user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
                .admin("ADMIN".equals(user.getRole()))
                .creator(creatorService.isCreator(user.getId()))
                .publicPosts(user.getPublicPosts() != null ? user.getPublicPosts() : true)
                .publicStars(user.getPublicStars() != null ? user.getPublicStars() : false)
                .publicLikes(user.getPublicLikes() != null ? user.getPublicLikes() : false)
                .publicHistory(user.getPublicHistory() != null ? user.getPublicHistory() : false)
                .searchable(user.getSearchable() != null ? user.getSearchable() : true)
                .notifyMessages(user.getNotifyMessages() != null ? user.getNotifyMessages() : true)
                .notifyReplies(user.getNotifyReplies() != null ? user.getNotifyReplies() : true)
                .notifyLikes(user.getNotifyLikes() != null ? user.getNotifyLikes() : false)
                .build();
    }
}
