package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStatsDTO {
    private Integer totalLikes;
    private Integer totalPosts;
    private Integer totalViews;
    private Integer requiredLikes;
    private Integer requiredPosts;
    private Integer requiredViews;
    private Boolean meetsRequirements;

    private String creatorLevel;
    private String creatorLevelName;
    private String nextLevel;
    private String nextLevelName;
    private Integer likesToNext;
    private Integer postsToNext;
    private Integer viewsToNext;
    private Integer progressPercent;
}
