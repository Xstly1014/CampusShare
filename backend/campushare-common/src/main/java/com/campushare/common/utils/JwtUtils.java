package com.campushare.common.utils;

import cn.hutool.core.util.StrUtil;
import com.campushare.common.constant.JwtConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
@Slf4j
@Component
public class JwtUtils {
    
    private final SecretKey secretKey;
    
    public JwtUtils() {
        // 使用HS256算法初始化密钥
        this.secretKey = Keys.hmacShaKeyFor(JwtConstants.JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 生成访问令牌
     */
    public String generateAccessToken(String userId, String username) {
        return generateToken(userId, username, JwtConstants.TOKEN_TYPE_ACCESS, 
                           JwtConstants.USER_TOKEN_EXPIRE_TIME);
    }
    
    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String userId) {
        return generateToken(userId, null, JwtConstants.TOKEN_TYPE_REFRESH, 
                           JwtConstants.REFRESH_TOKEN_EXPIRE_TIME);
    }
    
    /**
     * 生成令牌
     */
    private String generateToken(String userId, String username, String tokenType, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstants.CLAIMS_USER_ID, userId);
        claims.put(JwtConstants.CLAIMS_TOKEN_TYPE, tokenType);
        if (StrUtil.isNotBlank(username)) {
            claims.put(JwtConstants.CLAIMS_USERNAME, username);
        }
        
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(StrUtil.isNotBlank(username) ? username : userId)
                .issuer(JwtConstants.JWT_ISSUER)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
    
    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 验证Token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户ID
     */
    public String getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_USER_ID, String.class);
    }
    
    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_USERNAME, String.class);
    }
    
    /**
     * 获取Token类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_TOKEN_TYPE, String.class);
    }
    
    /**
     * 判断是否是访问令牌
     */
    public boolean isAccessToken(String token) {
        return JwtConstants.TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }
    
    /**
     * 判断是否是刷新令牌
     */
    public boolean isRefreshToken(String token) {
        return JwtConstants.TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }
    
    /**
     * 获取Token过期时间（秒）
     */
    public long getExpiration() {
        return JwtConstants.USER_TOKEN_EXPIRE_TIME / 1000;
    }
}