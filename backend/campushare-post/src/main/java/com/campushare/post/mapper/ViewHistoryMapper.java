package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.ViewHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ViewHistoryMapper extends BaseMapper<ViewHistory> {

    @Delete("DELETE FROM view_history")
    void deleteAllPhysical();

    @Select("SELECT COUNT(*) FROM view_history vh INNER JOIN posts p ON vh.post_id = p.id " +
            "WHERE vh.user_id = #{userId} AND p.deleted = 0")
    long countValidByUserId(@Param("userId") String userId);

    @Select("SELECT vh.post_id FROM view_history vh INNER JOIN posts p ON vh.post_id = p.id " +
            "WHERE vh.user_id = #{userId} AND p.deleted = 0 " +
            "ORDER BY vh.view_time DESC LIMIT #{offset}, #{size}")
    List<String> selectValidPostIdsPage(@Param("userId") String userId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);
}
