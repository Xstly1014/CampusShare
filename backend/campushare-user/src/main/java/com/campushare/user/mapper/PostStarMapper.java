package com.campushare.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.user.entity.PostStar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface PostStarMapper extends BaseMapper<PostStar> {

    @Delete("DELETE FROM post_stars")
    void deleteAllPhysical();
}
