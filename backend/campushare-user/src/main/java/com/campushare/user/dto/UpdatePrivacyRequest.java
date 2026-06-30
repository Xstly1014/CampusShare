package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePrivacyRequest {
    private Boolean publicPosts;
    private Boolean publicStars;
    private Boolean publicLikes;
    private Boolean publicHistory;
    private Boolean searchable;
}
