package com.campushare.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campushare.user.dto.CreatorApplicationItem;
import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;
import com.campushare.user.dto.CreatorVerifyRequest;

public interface CreatorService {
    CreatorStatsDTO getStats(String userId);
    CreatorStatusDTO getStatus(String userId);
    boolean isCreator(String userId);
    boolean isAdmin(String userId);
    void apply(String userId, CreatorApplyRequest request);
    IPage<CreatorApplicationItem> getApplicationList(String status, int page, int size);
    void verify(String adminId, Integer applicationId, CreatorVerifyRequest request);
}
