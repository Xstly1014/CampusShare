package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.CommentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    @Delete("DELETE FROM comment_likes")
    void deleteAllPhysical();
}
