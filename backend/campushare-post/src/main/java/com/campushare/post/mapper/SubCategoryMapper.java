package com.campushare.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campushare.post.entity.SubCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubCategoryMapper extends BaseMapper<SubCategory> {

    @Delete("DELETE FROM sub_categories")
    void deleteAllPhysical();
}
