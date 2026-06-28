package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.Post;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Delete("DELETE FROM posts")
    void deleteAllPhysical();

    @Select("SELECT COALESCE(SUM(view_count), 0) FROM posts WHERE author_id = #{userId} AND deleted = 0")
    long sumViewCountByAuthorId(@Param("userId") String userId);

    @Select("SELECT COALESCE(SUM(like_count), 0) FROM posts WHERE author_id = #{userId} AND deleted = 0")
    long sumLikeCountByAuthorId(@Param("userId") String userId);

    @Select("SELECT COALESCE(SUM(star_count), 0) FROM posts WHERE author_id = #{userId} AND deleted = 0")
    long sumStarCountByAuthorId(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM posts WHERE author_id = #{userId} AND deleted = 0")
    long countByAuthorId(@Param("userId") String userId);
}
