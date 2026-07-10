package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.ContextSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContextSummaryMapper extends BaseMapper<ContextSummary> {

    @Select("SELECT * FROM context_summaries WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT 1")
    ContextSummary findLatestBySessionId(@Param("sessionId") String sessionId);
}
