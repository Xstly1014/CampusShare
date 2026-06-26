package com.campushare.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.user.entity.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
