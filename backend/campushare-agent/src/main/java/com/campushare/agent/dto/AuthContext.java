package com.campushare.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthContext {
    private String userId;
    private String sessionId;
    private String authType;
    private Set<String> roles;
    private Set<String> permissions;
    private LocalDateTime expireTime;
    private String clientIp;
    private String deviceId;
    private String appVersion;
    private boolean isVip;
}