package com.campushare.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private String id;
    private String name;
    private String icon;
    private String color;
    private String type;
    private String description;
    private Integer sortOrder;
    private Integer postCount;
    private List<SubCategoryDTO> subCategories;
}
