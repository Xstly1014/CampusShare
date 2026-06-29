package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.CreatorApplicationItem;
import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;
import com.campushare.user.dto.CreatorVerifyRequest;
import com.campushare.user.entity.CreatorVerification;
import com.campushare.user.entity.User;
import com.campushare.user.feign.PostFeignClient;
import com.campushare.user.feign.UserPostStats;
import com.campushare.user.mapper.CreatorVerificationMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CreatorService;
import com.campushare.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorServiceImpl implements CreatorService {

    private static final int REQUIRED_LIKES = 10000;
    private static final int REQUIRED_POSTS = 50;

    private final CreatorVerificationMapper creatorVerificationMapper;
    private final UserMapper userMapper;
    private final PostFeignClient postFeignClient;
    private final NotificationService notificationService;

    @Override
    public CreatorStatsDTO getStats(String userId) {
        UserPostStats stats = postFeignClient.getUserStats(userId).getData();
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

    @Override
    public boolean isAdmin(String userId) {
        User user = userMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getRole());
    }

    @Override
    public IPage<CreatorApplicationItem> getApplicationList(String status, int page, int size) {
        Page<CreatorVerification> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CreatorVerification> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(CreatorVerification::getStatus, status);
        }
        wrapper.orderByDesc(CreatorVerification::getCreateTime);

        IPage<CreatorVerification> verificationPage = creatorVerificationMapper.selectPage(pageParam, wrapper);

        List<String> userIds = verificationPage.getRecords().stream()
                .map(CreatorVerification::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, User> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = Map.of();
        }

        return verificationPage.convert(v -> {
            User user = userMap.get(v.getUserId());
            return CreatorApplicationItem.builder()
                    .id(v.getId())
                    .userId(v.getUserId())
                    .username(user != null ? user.getUsername() : "未知用户")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .realName(v.getRealName())
                    .idCard(v.getIdCard())
                    .totalLikes(v.getTotalLikes())
                    .totalPosts(v.getTotalPosts())
                    .status(v.getStatus())
                    .rejectReason(v.getRejectReason())
                    .applyTime(v.getCreateTime())
                    .reviewTime(v.getReviewTime())
                    .build();
        });
    }

    @Override
    @Transactional
    public void verify(String adminId, Integer applicationId, CreatorVerifyRequest request) {
        if (!isAdmin(adminId)) {
            throw new BusinessException(4030, "无权限操作");
        }

        CreatorVerification verification = creatorVerificationMapper.selectById(applicationId);
        if (verification == null) {
            throw new BusinessException(4000, "申请不存在");
        }
        if (!"PENDING".equals(verification.getStatus())) {
            throw new BusinessException(4000, "该申请已审核");
        }

        if (request.isApproved()) {
            verification.setStatus("APPROVED");
            verification.setRejectReason(null);
            User applicant = userMapper.selectById(verification.getUserId());
            if (applicant != null) {
                applicant.setRole("CREATOR");
                userMapper.updateById(applicant);
            }
            notificationService.createSystemNotification(
                    verification.getUserId(),
                    "创作者认证通过",
                    "恭喜你！你的创作者认证申请已通过，现在可以享受创作者专属权益了。"
            );
        } else {
            verification.setStatus("REJECTED");
            verification.setRejectReason(request.getRejectReason());
            notificationService.createSystemNotification(
                    verification.getUserId(),
                    "创作者认证被驳回",
                    "你的创作者认证申请未通过，原因：" + request.getRejectReason()
            );
        }
        verification.setReviewerId(adminId);
        verification.setReviewTime(LocalDateTime.now());
        creatorVerificationMapper.updateById(verification);
    }
}
