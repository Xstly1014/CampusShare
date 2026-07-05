package com.campushare.agent.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.agent.entity.PromptVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * PromptVersion Mapper。
 *
 * 使用 MyBatis Plus BaseMapper，匹配 agent-service 现有 Mapper 风格。
 * 自定义查询用 LambdaQueryWrapper 在 Service 层组装，保持 Mapper 接口纯净。
 */
@Mapper
public interface PromptVersionMapper extends BaseMapper<PromptVersion> {

    /**
     * 查询指定版本号的记录。
     */
    default PromptVersion findByVersion(String version) {
        LambdaQueryWrapper<PromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptVersion::getVersion, version);
        return selectOne(wrapper);
    }

    /**
     * 查询当前 RELEASED 状态的最新版本。
     */
    default PromptVersion findReleasedLatest() {
        LambdaQueryWrapper<PromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptVersion::getStatus, "RELEASED")
               .orderByDesc(PromptVersion::getReleasedAt)
               .last("LIMIT 1");
        return selectOne(wrapper);
    }

    /**
     * 查询指定版本的前一个 RELEASED 版本（用于回滚）。
     */
    default PromptVersion findPreviousReleased(String currentVersion) {
        LambdaQueryWrapper<PromptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptVersion::getStatus, "RELEASED")
               .ne(PromptVersion::getVersion, currentVersion)
               .orderByDesc(PromptVersion::getReleasedAt)
               .last("LIMIT 1");
        return selectOne(wrapper);
    }
}
