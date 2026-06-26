package com.campushare.common.constant;

/**
 * Redis缓存常量
 */
public class RedisConstants {
    
    private RedisConstants() {}
    
    /**
     * 用户Token缓存前缀
     */
    public static final String USER_TOKEN_PREFIX = "campushare:user:token:";
    
    /**
     * 用户信息缓存前缀
     */
    public static final String USER_INFO_PREFIX = "campushare:user:info:";
    
    /**
     * 验证码缓存前缀
     */
    public static final String VERIFY_CODE_PREFIX = "campushare:verify:code:";
    
    /**
     * 验证码发送次数限制前缀
     */
    public static final String VERIFY_CODE_COUNT_PREFIX = "campushare:verify:count:";
    
    /**
     * 接口限流前缀
     */
    public static final String RATE_LIMIT_PREFIX = "campushare:rate:limit:";
    
    /**
     * 用户Token过期时间（2小时）
     */
    public static final long USER_TOKEN_EXPIRE_TIME = 7200000L;
    
    /**
     * 刷新Token过期时间（7天）
     */
    public static final long REFRESH_TOKEN_EXPIRE_TIME = 604800000L;
    
    /**
     * 验证码过期时间（5分钟）
     */
    public static final long VERIFY_CODE_EXPIRE_TIME = 300000L;
    
    /**
     * 验证码发送间隔（60秒）
     */
    public static final long VERIFY_CODE_INTERVAL = 60000L;
    
    /**
     * 验证码发送次数限制（每小时）
     */
    public static final int VERIFY_CODE_MAX_COUNT = 10;
}