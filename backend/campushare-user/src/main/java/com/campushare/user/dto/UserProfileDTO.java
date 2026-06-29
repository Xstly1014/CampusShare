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
    private boolean following;
    @JsonProperty("isSelf")
    private boolean self;
    @JsonProperty("isCreator")
    private boolean creator;
    @JsonProperty("isAdmin")
    private boolean admin;
}
