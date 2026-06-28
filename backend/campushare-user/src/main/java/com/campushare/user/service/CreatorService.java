package com.campushare.user.service;

import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;

public interface CreatorService {
    CreatorStatsDTO getStats(String userId);
    CreatorStatusDTO getStatus(String userId);
    boolean isCreator(String userId);
    void apply(String userId, CreatorApplyRequest request);
}
