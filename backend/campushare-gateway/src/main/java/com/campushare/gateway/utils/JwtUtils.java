package com.campushare.gateway.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtUtils {
    
    private static final String JWT_SECRET = "CampusShare2024SecretKeyForJwtTokenSigningMustBe256BitsLong";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String CLAIMS_USER_ID = "userId";
    private static final String CLAIMS_USERNAME = "username";
    private static final String CLAIMS_TOKEN_TYPE = "tokenType";
    
    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    
    public JwtUtils() {
        this.secretKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }
    
    public Claims parseToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }
    
    public Claims validateAndParse(String token) {
        try {
            return parseToken(token);
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("Token验证失败: {}", e.getMessage());
        }
        return null;
    }
    
    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIMS_TOKEN_TYPE, String.class));
    }
    
    public String getUserId(Claims claims) {
        return claims.get(CLAIMS_USER_ID, String.class);
    }
    
    public String getUsername(Claims claims) {
        return claims.get(CLAIMS_USERNAME, String.class);
    }
}
