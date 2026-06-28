package com.campushare.user.feign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPostStats {
    private long totalViews;
    private long totalLikes;
    private long totalStars;
    private long postCount;
}
