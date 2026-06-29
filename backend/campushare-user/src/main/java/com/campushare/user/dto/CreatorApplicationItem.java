package com.campushare.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CreatorApplicationItem {
    private Integer id;
    private String userId;
    private String username;
    private String avatarUrl;
    private String realName;
    private String idCard;
    private Integer totalLikes;
    private Integer totalPosts;
    private String status;
    private String rejectReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
}
