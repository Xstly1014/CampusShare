package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String id;
    private String username;
    private String avatarUrl;
    private String bio;
    private String email;
    private String phone;
    private long postCount;
    private long totalViews;
    private long totalLikes;
    private long totalStars;
    private long followerCount;
    private long followingCount;
    private boolean isFollowing;
    private boolean isSelf;
}
