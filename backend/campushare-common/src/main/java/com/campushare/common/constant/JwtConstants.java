package com.campushare.common.constant;

/**
 * JWT常量
 */
public class JwtConstants {
    
    private JwtConstants() {}
    
    /**
     * JWT Token请求头名称
     */
    public static final String TOKEN_HEADER = "Authorization";
    
    /**
     * JWT Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";
    
    /**
     * JWT Token密钥（生产环境应从配置中心读取）
     */
    public static final String JWT_SECRET = "CampusShare2024SecretKeyForJwtTokenSigningMustBe256BitsLong";
    
    /**
     * JWT Token签发者
     */
    public static final String JWT_ISSUER = "CampusShare";
    
    /**
     * JWT Token主题
     */
    public static final String JWT_SUBJECT = "CampusShare User";
    
    /**
     * Token类型
     */
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    
    /**
     * Claims中的用户ID Key
     */
    public static final String CLAIMS_USER_ID = "userId";
    
    /**
     * Claims中的用户名 Key
     */
    public static final String CLAIMS_USERNAME = "username";
    
    /**
     * Claims中的Token类型 Key
     */
    public static final String CLAIMS_TOKEN_TYPE = "tokenType";
    
    /**
     * 用户Token过期时间（毫秒）- 2小时
     */
    public static final long USER_TOKEN_EXPIRE_TIME = 2 * 60 * 60 * 1000L;
    
    /**
     * 刷新Token过期时间（毫秒）- 7天
     */
    public static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
}