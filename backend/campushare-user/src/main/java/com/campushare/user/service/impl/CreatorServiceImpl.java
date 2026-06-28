package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;
import com.campushare.user.dto.UserPostStats;
import com.campushare.user.entity.CreatorVerification;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.mapper.CreatorVerificationMapper;
import com.campushare.user.service.CreatorService;
import com.campushare.user.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorServiceImpl implements CreatorService {

    private static final int REQUIRED_LIKES = 10000;
    private static final int REQUIRED_POSTS = 50;

    private final CreatorVerificationMapper creatorVerificationMapper;
    private final PostService postService;

    @Override
    public CreatorStatsDTO getStats(String userId) {
        UserPostStats stats = postService.getMyPostStats(userId);
        int totalLikes = (int) stats.getTotalLikes();
        int totalPosts = (int) stats.getPostCount();
        return CreatorStatsDTO.builder()
                .totalLikes(totalLikes)
                .totalPosts(totalPosts)
                .requiredLikes(REQUIRED_LIKES)
                .requiredPosts(REQUIRED_POSTS)
                .meetsRequirements(totalLikes >= REQUIRED_LIKES && totalPosts >= REQUIRED_POSTS)
                .build();
    }

    @Override
    public CreatorStatusDTO getStatus(String userId) {
        LambdaQueryWrapper<CreatorVerification> wrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .orderByDesc(CreatorVerification::getCreateTime)
                .last("LIMIT 1");
        CreatorVerification verification = creatorVerificationMapper.selectOne(wrapper);

        if (verification == null) {
            return CreatorStatusDTO.builder()
                    .status("NONE")
                    .build();
        }

        return CreatorStatusDTO.builder()
                .status(verification.getStatus())
                .rejectReason(verification.getRejectReason())
                .applyTime(verification.getCreateTime())
                .reviewTime(verification.getReviewTime())
                .totalLikes(verification.getTotalLikes())
                .totalPosts(verification.getTotalPosts())
                .build();
    }

    @Override
    public boolean isCreator(String userId) {
        LambdaQueryWrapper<CreatorVerification> wrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .eq(CreatorVerification::getStatus, "APPROVED")
                .last("LIMIT 1");
        return creatorVerificationMapper.selectOne(wrapper) != null;
    }

    @Override
    @Transactional
    public void apply(String userId, CreatorApplyRequest request) {
        if (request.getRealName() == null || request.getRealName().trim().isEmpty()) {
            throw new BusinessException(4001, "请输入真实姓名");
        }
        if (request.getIdCard() == null || !request.getIdCard().matches("\\d{17}[\\dXx]")) {
            throw new BusinessException(4002, "请输入正确的身份证号");
        }

        CreatorStatusDTO currentStatus = getStatus(userId);
        if ("PENDING".equals(currentStatus.getStatus())) {
            throw new BusinessException(4003, "您的申请正在审核中，请耐心等待");
        }
        if ("APPROVED".equals(currentStatus.getStatus())) {
            throw new BusinessException(4004, "您已通过创作者认证");
        }

        CreatorStatsDTO stats = getStats(userId);
        if (!stats.getMeetsRequirements()) {
            throw new BusinessException(4005, "暂不满足认证条件：需获赞" + REQUIRED_LIKES + "以上且发布帖子" + REQUIRED_POSTS + "篇以上");
        }

        CreatorVerification verification = CreatorVerification.builder()
                .userId(userId)
                .realName(request.getRealName().trim())
                .idCard(request.getIdCard().toUpperCase())
                .idCardFront(request.getIdCardFront())
                .idCardBack(request.getIdCardBack())
                .totalLikes(stats.getTotalLikes())
                .totalPosts(stats.getTotalPosts())
                .status("PENDING")
                .build();

        creatorVerificationMapper.insert(verification);
    }
}
