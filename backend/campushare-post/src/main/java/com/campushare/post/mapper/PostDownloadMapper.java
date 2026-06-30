package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.PostDownload;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostDownloadMapper extends BaseMapper<PostDownload> {

    @Delete("DELETE FROM post_downloads")
    void deleteAllPhysical();

    @Select("SELECT pd.id, pd.post_id, pd.download_time FROM post_downloads pd " +
            "INNER JOIN posts p ON pd.post_id = p.id " +
            "WHERE pd.user_id = #{userId} AND p.deleted = 0 AND p.post_type = 'resource' " +
            "ORDER BY pd.download_time DESC LIMIT #{offset}, #{size}")
    List<PostDownload> selectValidPage(@Param("userId") String userId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("SELECT COUNT(*) FROM post_downloads pd INNER JOIN posts p ON pd.post_id = p.id " +
            "WHERE pd.user_id = #{userId} AND p.deleted = 0 AND p.post_type = 'resource'")
    long countValidByUserId(@Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM post_downloads pd INNER JOIN posts p ON pd.post_id = p.id " +
            "WHERE p.author_id = #{authorId} AND p.deleted = 0 AND p.post_type = 'resource'")
    long countDownloadsByAuthor(@Param("authorId") String authorId);

    @Select("SELECT p.category_id AS categoryId, COUNT(*) AS cnt FROM post_downloads pd " +
            "INNER JOIN posts p ON pd.post_id = p.id " +
            "WHERE pd.user_id = #{userId} AND p.deleted = 0 AND p.post_type = 'resource' AND p.category_id IS NOT NULL " +
            "GROUP BY p.category_id")
    List<Map<String, Object>> countDownloadsGroupByCategory(@Param("userId") String userId);
}
