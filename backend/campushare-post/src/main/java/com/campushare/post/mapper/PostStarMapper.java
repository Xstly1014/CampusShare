package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.PostStar;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostStarMapper extends BaseMapper<PostStar> {

    @Delete("DELETE FROM post_stars")
    void deleteAllPhysical();

    @Select("SELECT COUNT(*) FROM post_stars ps INNER JOIN posts p ON ps.post_id = p.id " +
            "WHERE ps.user_id = #{userId} AND p.deleted = 0")
    long countValidByUserId(@Param("userId") String userId);

    @Select("SELECT ps.post_id FROM post_stars ps INNER JOIN posts p ON ps.post_id = p.id " +
            "WHERE ps.user_id = #{userId} AND p.deleted = 0 " +
            "ORDER BY ps.create_time DESC LIMIT #{offset}, #{size}")
    List<String> selectValidPostIdsPage(@Param("userId") String userId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);
}
