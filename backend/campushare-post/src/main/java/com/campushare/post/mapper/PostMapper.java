package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.Post;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    @Select("SELECT COUNT(*) FROM posts WHERE author_id = #{userId} AND deleted = 0 AND post_type = 'resource'")
    long countResourceByAuthorId(@Param("userId") String userId);

    @Select("SELECT school_id AS schoolId, COUNT(*) AS cnt FROM posts " +
            "WHERE deleted = 0 AND status = 1 AND school_id IS NOT NULL AND category_id IS NULL " +
            "GROUP BY school_id")
    List<Map<String, Object>> countGroupBySchool();

    @Select("SELECT category_id AS categoryId, COUNT(*) AS cnt FROM posts " +
            "WHERE deleted = 0 AND status = 1 AND category_id IS NOT NULL " +
            "GROUP BY category_id")
    List<Map<String, Object>> countGroupByCategory();

    @Select("SELECT sub_category_id AS subCategoryId, COUNT(*) AS cnt FROM posts " +
            "WHERE deleted = 0 AND status = 1 AND sub_category_id IS NOT NULL " +
            "GROUP BY sub_category_id")
    List<Map<String, Object>> countGroupBySubCategory();

    @Select("SELECT category_id AS categoryId, COUNT(*) AS cnt FROM posts " +
            "WHERE author_id = #{userId} AND deleted = 0 AND post_type = 'resource' AND category_id IS NOT NULL " +
            "GROUP BY category_id")
    List<Map<String, Object>> countUploadsByAuthorGroupByCategory(@Param("userId") String userId);

    @Select("SELECT p.category_id AS categoryId, COUNT(*) AS cnt FROM view_history vh " +
            "INNER JOIN posts p ON vh.post_id = p.id " +
            "WHERE vh.user_id = #{userId} AND p.deleted = 0 AND p.category_id IS NOT NULL " +
            "GROUP BY p.category_id")
    List<Map<String, Object>> countViewsByUserGroupByCategory(@Param("userId") String userId);
}
