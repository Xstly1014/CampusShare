package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Delete("DELETE FROM categories")
    void deleteAllPhysical();
}
