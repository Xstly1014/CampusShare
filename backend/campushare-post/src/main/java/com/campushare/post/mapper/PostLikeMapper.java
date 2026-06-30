package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.PostLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    @Delete("DELETE FROM post_likes")
    void deleteAllPhysical();

    @Select("SELECT COUNT(*) FROM post_likes pl INNER JOIN posts p ON pl.post_id = p.id " +
            "WHERE pl.user_id = #{userId} AND p.deleted = 0")
    long countValidByUserId(@Param("userId") String userId);

    @Select("SELECT pl.post_id FROM post_likes pl INNER JOIN posts p ON pl.post_id = p.id " +
            "WHERE pl.user_id = #{userId} AND p.deleted = 0 " +
            "ORDER BY pl.create_time DESC LIMIT #{offset}, #{size}")
    List<String> selectValidPostIdsPage(@Param("userId") String userId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);
}
