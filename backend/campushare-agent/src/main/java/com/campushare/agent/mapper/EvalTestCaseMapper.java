package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.EvalTestCase;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EvalTestCaseMapper extends BaseMapper<EvalTestCase> {

    List<EvalTestCase> findByCategory(String category);

    List<EvalTestCase> findGoldenCases();

    List<EvalTestCase> findByPriority(Integer priority);
}