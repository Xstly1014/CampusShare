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

@Slf4j
@Component
public class JwtUtils {
    
    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    
    public JwtUtils() {
        this.secretKey = Keys.hmacShaKeyFor(JwtConstants.JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }
    
    public String generateAccessToken(String userId, String username) {
        return generateToken(userId, username, JwtConstants.TOKEN_TYPE_ACCESS, 
                           JwtConstants.USER_TOKEN_EXPIRE_TIME);
    }
    
    public String generateRefreshToken(String userId) {
        return generateToken(userId, null, JwtConstants.TOKEN_TYPE_REFRESH, 
                           JwtConstants.REFRESH_TOKEN_EXPIRE_TIME);
    }
    
    private String generateToken(String userId, String username, String tokenType, long expiration) {
        Map<String, Object> claims = new HashMap<>(4);
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
    
    public Claims parseToken(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }
    
    public boolean validateToken(String token) {
        try {
            jwtParser.parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Token验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    public String getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_USER_ID, String.class);
    }
    
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_USERNAME, String.class);
    }
    
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.CLAIMS_TOKEN_TYPE, String.class);
    }
    
    public boolean isAccessToken(String token) {
        return JwtConstants.TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }
    
    public boolean isRefreshToken(String token) {
        return JwtConstants.TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }
    
    public long getExpiration() {
        return JwtConstants.USER_TOKEN_EXPIRE_TIME / 1000;
    }
}
