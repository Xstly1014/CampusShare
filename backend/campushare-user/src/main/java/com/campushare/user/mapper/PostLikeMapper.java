package com.campushare.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.user.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {

    @Delete("DELETE FROM post_likes")
    void deleteAllPhysical();
}
