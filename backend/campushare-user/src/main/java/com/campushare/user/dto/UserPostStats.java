package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPostStats {
    /** Total views across all of the user's posts */
    private long totalViews;
    /** Total likes (获赞) across all of the user's posts */
    private long totalLikes;
    /** Total stars (被收藏) across all of the user's posts */
    private long totalStars;
    /** Total number of posts authored by the user */
    private long postCount;
}
