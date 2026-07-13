package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.UserMemoryEvidence;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMemoryEvidenceMapper extends BaseMapper<UserMemoryEvidence> {

    List<UserMemoryEvidence> findByUserId(String userId);

    List<UserMemoryEvidence> findByMemoryKey(String userId, String memoryKey);

    List<UserMemoryEvidence> findByEvidenceType(String evidenceType);
}
