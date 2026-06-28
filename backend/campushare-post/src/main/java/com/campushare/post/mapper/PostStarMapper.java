package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.PostStar;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostStarMapper extends BaseMapper<PostStar> {

    @Delete("DELETE FROM post_stars")
    void deleteAllPhysical();
}
