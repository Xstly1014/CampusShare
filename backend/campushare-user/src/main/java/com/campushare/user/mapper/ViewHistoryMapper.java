package com.campushare.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.user.entity.ViewHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface ViewHistoryMapper extends BaseMapper<ViewHistory> {

    @Delete("DELETE FROM view_history")
    void deleteAllPhysical();
}
