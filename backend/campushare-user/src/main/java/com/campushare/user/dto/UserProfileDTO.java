package com.campushare.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isFollowing")
    private boolean isFollowing;
    @JsonProperty("isSelf")
    private boolean isSelf;
    @JsonProperty("isCreator")
    private boolean isCreator;
}
