package com.campushare.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseStats {
    private long uploadCount;
    private long downloadCount;
    private long totalViews;
    private long totalLikes;
    private long totalStars;
    private long totalDownloadsOfMyPosts;
    private Map<String, Long> uploadsByCategory;
    private Map<String, Long> downloadsByCategory;

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
        private long downloadCount;
    }

    private List<CategoryStat> categoryStats;
}
