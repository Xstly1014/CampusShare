package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.AgentSessionEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话状态转移事件 Mapper（ADR-068）。
 */
@Mapper
public interface AgentSessionEventMapper extends BaseMapper<AgentSessionEvent> {
}
