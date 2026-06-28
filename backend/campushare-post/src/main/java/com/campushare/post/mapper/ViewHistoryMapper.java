package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.ViewHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ViewHistoryMapper extends BaseMapper<ViewHistory> {

    @Delete("DELETE FROM view_history")
    void deleteAllPhysical();
}
