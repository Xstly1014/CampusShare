package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryDTO {
    private String id;
    private String categoryId;
    private String name;
    private String icon;
    private Integer sortOrder;
    private Integer postCount;
}
