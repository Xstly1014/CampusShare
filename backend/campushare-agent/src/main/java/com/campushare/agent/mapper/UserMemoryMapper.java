package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户长期记忆 Mapper（ADR-059）。
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {
}
