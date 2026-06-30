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
public class WarehouseStats {
    private long uploadCount;
    private long viewCount;
    private long totalViews;
    private long totalLikes;
    private long totalStars;
    private List<CategoryStat> categoryStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private String categoryId;
        private String categoryName;
        private String color;
        private String icon;
        private long uploadCount;
        private long viewCount;
    }
}
