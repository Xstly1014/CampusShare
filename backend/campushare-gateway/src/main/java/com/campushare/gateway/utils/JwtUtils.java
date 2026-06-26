package com.campushare.gateway.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类（网关版）
 */
@Slf4j
@Component
public class JwtUtils {
    
    private static final String JWT_SECRET = "CampusShare2024SecretKeyForJwtTokenSigningMustBe256BitsLong";
    private static final String TOKEN_TYPE_ACCESS = "access";
    
    private final SecretKey secretKey;
    
    public JwtUtils() {
        this.secretKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
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
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("Token验证失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 获取Token类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("tokenType", String.class);
    }
    
    /**
     * 判断是否是访问令牌
     */
    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }
}