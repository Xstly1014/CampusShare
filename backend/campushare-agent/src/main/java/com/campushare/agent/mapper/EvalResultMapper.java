package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.EvalResult;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface EvalResultMapper extends BaseMapper<EvalResult> {

    List<EvalResult> findByRunId(String runId);

    List<EvalResult> findByTestCaseId(String testCaseId);

    List<EvalResult> findFailedByRunId(String runId);

    Map<String, Object> getRunSummary(String runId);

    List<Map<String, Object>> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> getTrendByDay(LocalDateTime startTime, LocalDateTime endTime);
}