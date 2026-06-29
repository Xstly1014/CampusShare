package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStatusDTO {
    private String status;
    private String creatorLevel;
    private Boolean hasPendingApplication;
    private String rejectReason;
    private LocalDateTime applyTime;
    private LocalDateTime reviewTime;
    private Integer totalLikes;
    private Integer totalPosts;
}
