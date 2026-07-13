package com.campushare.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campushare.agent.entity.SecurityAuditLog;
import com.campushare.agent.mapper.SecurityAuditLogMapper;
import com.campushare.agent.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl extends ServiceImpl<SecurityAuditLogMapper, SecurityAuditLog>
        implements SecurityAuditService {

    @Override
    @Transactional
    public void logInput(String sessionId, String userId, String turnId, String inputText) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .turnId(turnId)
                .auditType("INPUT")
                .auditLevel("INFO")
                .inputText(truncate(inputText, 2000))
                .blocked(false)
                .build();
        save(auditLog);
        log.debug("Logged input: session={}, user={}", sessionId, userId);
    }

    @Override
    @Transactional
    public void logOutput(String sessionId, String userId, String turnId, String outputText) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .turnId(turnId)
                .auditType("OUTPUT")
                .auditLevel("INFO")
                .outputText(truncate(outputText, 2000))
                .blocked(false)
                .build();
        save(auditLog);
        log.debug("Logged output: session={}, user={}", sessionId, userId);
    }

    @Override
    @Transactional
    public void logThreat(String sessionId, String userId, String turnId, String auditType,
                          String auditLevel, String detectedThreat, String actionTaken,
                          Double confidence, Boolean blocked) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .turnId(turnId)
                .auditType(auditType)
                .auditLevel(auditLevel)
                .detectedThreat(detectedThreat)
                .actionTaken(actionTaken)
                .confidence(confidence)
                .blocked(blocked)
                .build();
        save(auditLog);
        log.warn("Logged threat: session={}, type={}, threat={}, blocked={}",
                sessionId, auditType, detectedThreat, blocked);
    }

    @Override
    @Transactional
    public void logToolCall(String sessionId, String userId, String turnId, String toolName,
                            String toolParameters, Boolean blocked, String errorMessage) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .turnId(turnId)
                .auditType("TOOL_CALL")
                .auditLevel(blocked ? "WARN" : "INFO")
                .toolName(toolName)
                .toolParameters(truncate(toolParameters, 1000))
                .blocked(blocked)
                .errorMessage(errorMessage)
                .build();
        save(auditLog);
        log.debug("Logged tool call: session={}, tool={}, blocked={}", sessionId, toolName, blocked);
    }

    @Override
    @Transactional
    public void logViolation(String sessionId, String userId, String turnId, String violationType,
                             String inputText, String outputText, String actionTaken) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .sessionId(sessionId)
                .userId(userId)
                .turnId(turnId)
                .auditType("VIOLATION")
                .auditLevel("WARN")
                .detectedThreat(violationType)
                .inputText(truncate(inputText, 500))
                .outputText(truncate(outputText, 1000))
                .actionTaken(actionTaken)
                .blocked(false)
                .build();
        save(auditLog);
        log.warn("Logged violation: session={}, type={}", sessionId, violationType);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
