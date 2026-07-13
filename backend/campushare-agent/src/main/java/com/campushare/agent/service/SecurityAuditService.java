package com.campushare.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campushare.agent.entity.SecurityAuditLog;

public interface SecurityAuditService extends IService<SecurityAuditLog> {

    void logInput(String sessionId, String userId, String turnId, String inputText);

    void logOutput(String sessionId, String userId, String turnId, String outputText);

    void logThreat(String sessionId, String userId, String turnId, String auditType,
                   String auditLevel, String detectedThreat, String actionTaken,
                   Double confidence, Boolean blocked);

    void logToolCall(String sessionId, String userId, String turnId, String toolName,
                     String toolParameters, Boolean blocked, String errorMessage);

    void logViolation(String sessionId, String userId, String turnId, String violationType,
                      String inputText, String outputText, String actionTaken);
}
