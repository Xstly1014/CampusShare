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
    private Integer requiredLikes;
    private Integer requiredPosts;
    private Boolean meetsRequirements;
}
