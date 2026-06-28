package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.PostLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    @Delete("DELETE FROM post_likes")
    void deleteAllPhysical();
}
