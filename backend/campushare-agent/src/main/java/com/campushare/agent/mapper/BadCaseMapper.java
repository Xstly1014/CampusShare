package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.BadCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BadCaseMapper extends BaseMapper<BadCase> {

    @Select("SELECT * FROM agent_bad_cases WHERE status = #{status} ORDER BY created_at DESC")
    List<BadCase> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM agent_bad_cases WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<BadCase> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM agent_bad_cases WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<BadCase> findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT b.* FROM agent_bad_cases b JOIN agent_turns t ON b.turn_id = t.id WHERE t.status = 'ERROR' ORDER BY t.created_at DESC LIMIT #{limit}")
    List<BadCase> findFromErrorTurns(@Param("limit") Integer limit);

    @Select("SELECT b.* FROM agent_bad_cases b JOIN agent_turns t ON b.turn_id = t.id WHERE t.dislike_count > 0 ORDER BY t.dislike_count DESC, t.created_at DESC LIMIT #{limit}")
    List<BadCase> findFromDislikedTurns(@Param("limit") Integer limit);

    @Select("SELECT b.* FROM agent_bad_cases b WHERE b.first_seen_at >= #{startTime} ORDER BY b.created_at DESC")
    List<BadCase> findByTimeRange(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT bad_case_type, COUNT(*) as count FROM agent_bad_cases WHERE created_at >= #{startTime} GROUP BY bad_case_type")
    List<Map<String, Object>> countByType(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT status, COUNT(*) as count FROM agent_bad_cases GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT severity, COUNT(*) as count FROM agent_bad_cases WHERE created_at >= #{startTime} GROUP BY severity")
    List<Map<String, Object>> countBySeverity(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COUNT(*) FROM agent_bad_cases WHERE created_at >= #{startTime} AND converted_to_test_case = false")
    Integer countUnconvertedSince(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COUNT(*) FROM agent_bad_cases WHERE status = 'NEW'")
    Integer countNewCases();
}