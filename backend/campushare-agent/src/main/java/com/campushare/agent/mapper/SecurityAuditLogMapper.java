package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.SecurityAuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SecurityAuditLogMapper extends BaseMapper<SecurityAuditLog> {

    List<SecurityAuditLog> findByUserId(String userId);

    List<SecurityAuditLog> findBySessionId(String sessionId);

    List<SecurityAuditLog> findByAuditType(String auditType);

    List<SecurityAuditLog> findBlockedLogs();
}
