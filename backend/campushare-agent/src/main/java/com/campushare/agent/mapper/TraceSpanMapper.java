package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.TraceSpan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TraceSpanMapper extends BaseMapper<TraceSpan> {

    List<TraceSpan> findByTraceId(String traceId);

    List<TraceSpan> findBySessionId(String sessionId);

    List<TraceSpan> findByUserId(String userId);

    List<TraceSpan> findBySpanType(String spanType);

    List<TraceSpan> findByStatus(String status);

    List<TraceSpan> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> getAvgDurationBySpanType(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> getErrorCountBySpanType(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> getTokenUsageByModel(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);
}
