package com.campushare.user.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.common.constant.RedisConstants;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.*;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtils jwtUtils;
    
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = findByAccount(request.getAccount());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        
        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.USER_LOGIN_ERROR);
        }
        
        // 3. 检查账号状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN);
        }
        
        // 4. 生成Token
        String token = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        
        // 5. 保存Token到Redis
        saveTokenToRedis(user.getId(), token);
        
        // 6. 构建响应
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
        // 1. 验证验证码
        String key = RedisConstants.VERIFY_CODE_PREFIX + request.getAccount();
        String cachedCode = redisTemplate.opsForValue().get(key);
        log.info("验证验证码 - key: {}, 缓存验证码: {}, 用户输入: {}", key, cachedCode, request.getVerifyCode());
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }
        
        // 2. 删除已使用的验证码
        redisTemplate.delete(RedisConstants.VERIFY_CODE_PREFIX + request.getAccount());
        
        // 3. 检查账号是否已存在
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
        
        // 4. 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .email("email".equals(request.getRegisterType()) ? request.getAccount() : null)
                .phone("phone".equals(request.getRegisterType()) ? request.getAccount() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(1)
                .build();
        
        userMapper.insert(user);
        
        // 5. 生成Token
        String token = jwtUtils.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        
        // 6. 保存Token到Redis
        saveTokenToRedis(user.getId(), token);
        
        log.info("用户注册成功: {}", user.getUsername());
        
        // 7. 构建响应
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getExpiration())
                .user(convertToUserDTO(user))
                .build();
    }
    
    @Override
    public void sendVerifyCode(String account, String type) {
        // 1. 检查发送频率
        String countKey = RedisConstants.VERIFY_CODE_COUNT_PREFIX + account;
        String countStr = redisTemplate.opsForValue().get(countKey);
        int count = StrUtil.isBlank(countStr) ? 0 : Integer.parseInt(countStr);
        if (count >= RedisConstants.VERIFY_CODE_MAX_COUNT) {
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_TOO_FREQUENT);
        }
        
        // 2. 生成6位随机验证码
        String verifyCode = String.format("%06d", (int) ((Math.random() * 9 + 1) * 100000));
        
        // 3. 保存验证码到Redis
        redisTemplate.opsForValue().set(
            RedisConstants.VERIFY_CODE_PREFIX + account,
            verifyCode,
            RedisConstants.VERIFY_CODE_EXPIRE_TIME,
            TimeUnit.MILLISECONDS
        );
        
        // 4. 增加发送次数
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, 3600000, TimeUnit.MILLISECONDS);
        
        // 5. 实际应调用短信/邮件服务发送验证码
        log.info("发送验证码到 {}: {}", account, verifyCode);
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
            // Check if username is taken by another user
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

        // If already bound, must verify original email first (unless realName verified)
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            if (!Boolean.TRUE.equals(request.getRealNameVerify())) {
                // Must verify original email
                if (request.getOriginalAccount() == null || request.getOriginalVerifyCode() == null) {
                    throw new BusinessException(4001, "换绑邮箱需先验证原邮箱");
                }
                if (!request.getOriginalAccount().equals(user.getEmail())) {
                    throw new BusinessException(4001, "原邮箱不匹配");
                }
                verifyCode(request.getOriginalAccount(), request.getOriginalVerifyCode());
            }
        }

        // Verify new email code
        verifyCode(request.getNewAccount(), request.getNewVerifyCode());

        // Check if email already used by another user
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

        // If already bound, must verify original phone first (unless realName verified)
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

        // Verify new phone code
        verifyCode(request.getNewAccount(), request.getNewVerifyCode());

        // Check if phone already used by another user
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
        // 预留接口：实名认证逻辑暂不实现
        // 未来可对接第三方实名认证服务，认证通过后在 Redis 中标记该用户可跳过原绑定验证
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
        // 1. 验证验证码
        String key = RedisConstants.VERIFY_CODE_PREFIX + request.getAccount();
        String cachedCode = redisTemplate.opsForValue().get(key);
        log.info("验证验证码 - key: {}, 缓存验证码: {}, 用户输入: {}", key, cachedCode, request.getVerifyCode());
        if (StrUtil.isBlank(cachedCode) || !cachedCode.equals(request.getVerifyCode())) {
            throw new BusinessException(ResultCode.VERIFY_CODE_ERROR);
        }
        
        // 2. 查询用户
        User user = findByAccount(request.getAccount());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        
        // 3. 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        
        // 4. 删除已使用的验证码
        redisTemplate.delete(RedisConstants.VERIFY_CODE_PREFIX + request.getAccount());
        
        log.info("用户重置密码成功: {}", user.getUsername());
    }
    
    /**
     * 根据账号查询用户
     */
    private User findByAccount(String account) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (account.contains("@")) {
            // 邮箱登录
            wrapper.eq(User::getEmail, account);
        } else if (account.matches("^1[3-9]\\d{9}$")) {
            // 手机号登录
            wrapper.eq(User::getPhone, account);
        } else {
            // 不支持用户名登录（昵称可修改，不适合作为登录账号）
            throw new BusinessException(ResultCode.USER_ACCOUNT_NOT_EXIST);
        }
        return userMapper.selectOne(wrapper);
    }
    
    /**
     * 保存Token到Redis
     */
    private void saveTokenToRedis(String userId, String token) {
        String tokenKey = RedisConstants.USER_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(
            tokenKey,
            token,
            RedisConstants.USER_TOKEN_EXPIRE_TIME,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 转换用户实体为DTO
     */
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
                .build();
    }
}