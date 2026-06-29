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

    private static final int JUNIOR_REQUIRED_POSTS = 50;
    private static final int JUNIOR_REQUIRED_LIKES = 10000;

    private static final int INTERMEDIATE_REQUIRED_POSTS = 100;
    private static final int INTERMEDIATE_REQUIRED_LIKES = 50000;
    private static final int INTERMEDIATE_REQUIRED_VIEWS = 500000;

    private static final int SENIOR_REQUIRED_POSTS = 300;
    private static final int SENIOR_REQUIRED_LIKES = 200000;
    private static final int SENIOR_REQUIRED_VIEWS = 2000000;

    private final CreatorVerificationMapper creatorVerificationMapper;
    private final UserMapper userMapper;
    private final PostFeignClient postFeignClient;
    private final NotificationService notificationService;

    private String calculateLevel(int totalPosts, long totalLikes, long totalViews) {
        if (totalPosts >= SENIOR_REQUIRED_POSTS && totalLikes >= SENIOR_REQUIRED_LIKES && totalViews >= SENIOR_REQUIRED_VIEWS) {
            return "SENIOR";
        }
        if (totalPosts >= INTERMEDIATE_REQUIRED_POSTS && totalLikes >= INTERMEDIATE_REQUIRED_LIKES && totalViews >= INTERMEDIATE_REQUIRED_VIEWS) {
            return "INTERMEDIATE";
        }
        if (totalPosts >= JUNIOR_REQUIRED_POSTS && totalLikes >= JUNIOR_REQUIRED_LIKES) {
            return "JUNIOR";
        }
        return "NONE";
    }

    private String getLevelName(String level) {
        switch (level) {
            case "JUNIOR": return "初级创作者";
            case "INTERMEDIATE": return "中级创作者";
            case "SENIOR": return "高级创作者";
            case "AUTHORITY": return "权威创作者";
            default: return "普通用户";
        }
    }

    private int getLevelOrder(String level) {
        switch (level) {
            case "AUTHORITY": return 4;
            case "SENIOR": return 3;
            case "INTERMEDIATE": return 2;
            case "JUNIOR": return 1;
            default: return 0;
        }
    }

    @Override
    public CreatorStatsDTO getStats(String userId) {
        UserPostStats stats = postFeignClient.getUserStats(userId).getData();
        int totalLikes = (int) stats.getTotalLikes();
        int totalPosts = (int) stats.getPostCount();
        long totalViews = stats.getTotalViews();

        User user = userMapper.selectById(userId);
        String currentLevel = user != null && user.getCreatorLevel() != null ? user.getCreatorLevel() : "NONE";

        String calculatedLevel = calculateLevel(totalPosts, totalLikes, totalViews);

        if (getLevelOrder(calculatedLevel) > getLevelOrder(currentLevel) && !"AUTHORITY".equals(currentLevel)) {
            currentLevel = calculatedLevel;
            if (user != null) {
                user.setCreatorLevel(currentLevel);
                if ("NONE".equals(user.getRole()) || user.getRole() == null) {
                    user.setRole("USER");
                }
                userMapper.updateById(user);

                if (!"NONE".equals(currentLevel)) {
                    notificationService.createSystemNotification(
                            userId,
                            "创作者等级提升",
                            "恭喜你！你的创作者等级已提升为" + getLevelName(currentLevel) + "。"
                    );
                }
            }
        }

        String nextLevel = null;
        String nextLevelName = null;
        int likesToNext = 0;
        int postsToNext = 0;
        int viewsToNext = 0;

        switch (currentLevel) {
            case "NONE":
                nextLevel = "JUNIOR";
                nextLevelName = getLevelName("JUNIOR");
                postsToNext = Math.max(0, JUNIOR_REQUIRED_POSTS - totalPosts);
                likesToNext = Math.max(0, JUNIOR_REQUIRED_LIKES - totalLikes);
                break;
            case "JUNIOR":
                nextLevel = "INTERMEDIATE";
                nextLevelName = getLevelName("INTERMEDIATE");
                postsToNext = Math.max(0, INTERMEDIATE_REQUIRED_POSTS - totalPosts);
                likesToNext = Math.max(0, INTERMEDIATE_REQUIRED_LIKES - totalLikes);
                viewsToNext = Math.max(0, (int)(INTERMEDIATE_REQUIRED_VIEWS - totalViews));
                break;
            case "INTERMEDIATE":
                nextLevel = "SENIOR";
                nextLevelName = getLevelName("SENIOR");
                postsToNext = Math.max(0, SENIOR_REQUIRED_POSTS - totalPosts);
                likesToNext = Math.max(0, SENIOR_REQUIRED_LIKES - totalLikes);
                viewsToNext = Math.max(0, (int)(SENIOR_REQUIRED_VIEWS - totalViews));
                break;
            case "SENIOR":
                nextLevel = "AUTHORITY";
                nextLevelName = getLevelName("AUTHORITY");
                break;
            case "AUTHORITY":
                break;
        }

        int progressPercent = 0;
        if ("JUNIOR".equals(nextLevel)) {
            double postProgress = Math.min(100, (double) totalPosts / JUNIOR_REQUIRED_POSTS * 100);
            double likeProgress = Math.min(100, (double) totalLikes / JUNIOR_REQUIRED_LIKES * 100);
            progressPercent = (int) Math.min(postProgress, likeProgress);
        } else if ("INTERMEDIATE".equals(nextLevel)) {
            double postProgress = Math.min(100, (double) totalPosts / INTERMEDIATE_REQUIRED_POSTS * 100);
            double likeProgress = Math.min(100, (double) totalLikes / INTERMEDIATE_REQUIRED_LIKES * 100);
            double viewProgress = Math.min(100, (double) totalViews / INTERMEDIATE_REQUIRED_VIEWS * 100);
            progressPercent = (int) Math.min(Math.min(postProgress, likeProgress), viewProgress);
        } else if ("SENIOR".equals(nextLevel)) {
            double postProgress = Math.min(100, (double) totalPosts / SENIOR_REQUIRED_POSTS * 100);
            double likeProgress = Math.min(100, (double) totalLikes / SENIOR_REQUIRED_LIKES * 100);
            double viewProgress = Math.min(100, (double) totalViews / SENIOR_REQUIRED_VIEWS * 100);
            progressPercent = (int) Math.min(Math.min(postProgress, likeProgress), viewProgress);
        } else if ("AUTHORITY".equals(currentLevel)) {
            progressPercent = 100;
        }

        return CreatorStatsDTO.builder()
                .totalLikes(totalLikes)
                .totalPosts(totalPosts)
                .totalViews((int) totalViews)
                .requiredLikes(JUNIOR_REQUIRED_LIKES)
                .requiredPosts(JUNIOR_REQUIRED_POSTS)
                .meetsRequirements(totalLikes >= JUNIOR_REQUIRED_LIKES && totalPosts >= JUNIOR_REQUIRED_POSTS)
                .creatorLevel(currentLevel)
                .creatorLevelName(getLevelName(currentLevel))
                .nextLevel(nextLevel)
                .nextLevelName(nextLevelName)
                .likesToNext(likesToNext)
                .postsToNext(postsToNext)
                .viewsToNext(viewsToNext)
                .progressPercent(progressPercent)
                .build();
    }

    @Override
    public CreatorStatusDTO getStatus(String userId) {
        User user = userMapper.selectById(userId);
        String creatorLevel = user != null && user.getCreatorLevel() != null ? user.getCreatorLevel() : "NONE";

        LambdaQueryWrapper<CreatorVerification> pendingWrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .eq(CreatorVerification::getStatus, "PENDING");
        long pendingCount = creatorVerificationMapper.selectCount(pendingWrapper);
        boolean hasPendingApplication = pendingCount > 0;

        LambdaQueryWrapper<CreatorVerification> wrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .orderByDesc(CreatorVerification::getCreateTime)
                .last("LIMIT 1");
        CreatorVerification verification = creatorVerificationMapper.selectOne(wrapper);

        String status;
        String rejectReason = null;
        LocalDateTime applyTime = null;
        LocalDateTime reviewTime = null;
        Integer totalLikes = null;
        Integer totalPosts = null;

        if (verification == null) {
            if ("NONE".equals(creatorLevel)) {
                status = "NONE";
            } else {
                status = "APPROVED";
            }
        } else {
            status = verification.getStatus();
            rejectReason = verification.getRejectReason();
            applyTime = verification.getCreateTime();
            reviewTime = verification.getReviewTime();
            totalLikes = verification.getTotalLikes();
            totalPosts = verification.getTotalPosts();
        }

        return CreatorStatusDTO.builder()
                .status(status)
                .creatorLevel(creatorLevel)
                .hasPendingApplication(hasPendingApplication)
                .rejectReason(rejectReason)
                .applyTime(applyTime)
                .reviewTime(reviewTime)
                .totalLikes(totalLikes)
                .totalPosts(totalPosts)
                .build();
    }

    @Override
    public boolean isCreator(String userId) {
        User user = userMapper.selectById(userId);
        return user != null && "CREATOR".equals(user.getRole());
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

        LambdaQueryWrapper<CreatorVerification> pendingWrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .eq(CreatorVerification::getStatus, "PENDING");
        if (creatorVerificationMapper.selectCount(pendingWrapper) > 0) {
            throw new BusinessException(4003, "您的申请正在审核中，请耐心等待");
        }

        User user = userMapper.selectById(userId);
        if (user != null && "CREATOR".equals(user.getRole()) && !"NONE".equals(user.getCreatorLevel())) {
            throw new BusinessException(4004, "您已通过创作者认证");
        }

        CreatorStatsDTO stats = getStats(userId);
        if (!stats.getMeetsRequirements()) {
            throw new BusinessException(4005, "暂不满足认证条件：需获赞" + JUNIOR_REQUIRED_LIKES + "以上且发布帖子" + JUNIOR_REQUIRED_POSTS + "篇以上");
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
                .verificationType("INITIAL")
                .build();

        creatorVerificationMapper.insert(verification);

        notificationService.createSystemNotification(
                userId,
                "创作者认证申请已提交",
                "你的创作者认证申请已提交成功，我们将在七个工作日内完成审核，请耐心等待。"
        );

        User applicant = userMapper.selectById(userId);
        String applicantName = applicant != null ? applicant.getUsername() : "用户";
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        for (User admin : admins) {
            notificationService.createSystemNotification(
                    admin.getId(),
                    "新的创作者认证申请",
                    applicantName + " 提交了创作者认证申请，请在七个工作日内完成审核。"
            );
        }
    }

    @Override
    @Transactional
    public void applyAuthority(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(4000, "用户不存在");
        }

        String currentLevel = user.getCreatorLevel();
        if (!"SENIOR".equals(currentLevel)) {
            throw new BusinessException(4006, "只有高级创作者才能申请权威创作者认证");
        }

        LambdaQueryWrapper<CreatorVerification> pendingWrapper = new LambdaQueryWrapper<CreatorVerification>()
                .eq(CreatorVerification::getUserId, userId)
                .eq(CreatorVerification::getStatus, "PENDING");
        if (creatorVerificationMapper.selectCount(pendingWrapper) > 0) {
            throw new BusinessException(4003, "您的申请正在审核中，请耐心等待");
        }

        CreatorVerification verification = CreatorVerification.builder()
                .userId(userId)
                .status("PENDING")
                .verificationType("AUTHORITY")
                .build();

        creatorVerificationMapper.insert(verification);

        notificationService.createSystemNotification(
                userId,
                "权威创作者申请已提交",
                "你的权威创作者认证申请已提交成功，我们将在七个工作日内完成审核，请耐心等待。"
        );

        String applicantName = user.getUsername() != null ? user.getUsername() : "用户";
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        for (User admin : admins) {
            notificationService.createSystemNotification(
                    admin.getId(),
                    "新的权威创作者申请",
                    applicantName + " 提交了权威创作者认证申请，请在七个工作日内完成审核。"
            );
        }
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
                    .verificationType(v.getVerificationType() != null ? v.getVerificationType() : "INITIAL")
                    .totalLikes(v.getTotalLikes())
                    .totalPosts(v.getTotalPosts())
                    .status(v.getStatus())
                    .rejectReason(v.getRejectReason())
                    .reviewNote(v.getReviewNote())
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

        String verificationType = verification.getVerificationType() != null ? verification.getVerificationType() : "INITIAL";
        User applicant = userMapper.selectById(verification.getUserId());

        if (request.isApproved()) {
            verification.setStatus("APPROVED");
            verification.setRejectReason(null);
            verification.setReviewNote(request.getReviewNote());

            if (applicant != null) {
                if ("INITIAL".equals(verificationType)) {
                    applicant.setRole("CREATOR");
                    if (applicant.getCreatorLevel() == null || "NONE".equals(applicant.getCreatorLevel())) {
                        applicant.setCreatorLevel("JUNIOR");
                    }
                } else if ("AUTHORITY".equals(verificationType)) {
                    applicant.setCreatorLevel("AUTHORITY");
                }
                userMapper.updateById(applicant);
            }

            if ("INITIAL".equals(verificationType)) {
                notificationService.createSystemNotification(
                        verification.getUserId(),
                        "创作者认证通过",
                        "恭喜你！你的创作者认证申请已通过，你已成为初级创作者，现在可以享受创作者专属权益了。"
                );
            } else {
                notificationService.createSystemNotification(
                        verification.getUserId(),
                        "权威创作者认证通过",
                        "恭喜你！你的权威创作者认证申请已通过，你已成为权威创作者。"
                );
            }
        } else {
            verification.setStatus("REJECTED");
            verification.setRejectReason(request.getRejectReason());
            verification.setReviewNote(request.getReviewNote());

            if ("AUTHORITY".equals(verificationType) && applicant != null) {
                if (!"SENIOR".equals(applicant.getCreatorLevel())) {
                    applicant.setCreatorLevel("SENIOR");
                    userMapper.updateById(applicant);
                }
            }

            if ("INITIAL".equals(verificationType)) {
                notificationService.createSystemNotification(
                        verification.getUserId(),
                        "创作者认证被驳回",
                        "你的创作者认证申请未通过，原因：" + (request.getRejectReason() != null ? request.getRejectReason() : "不符合认证条件")
                );
            } else {
                notificationService.createSystemNotification(
                        verification.getUserId(),
                        "权威创作者认证被驳回",
                        "你的权威创作者认证申请未通过，原因：" + (request.getRejectReason() != null ? request.getRejectReason() : "不符合认证条件") + "。你仍保持高级创作者等级。"
                );
            }
        }
        verification.setReviewerId(adminId);
        verification.setReviewTime(LocalDateTime.now());
        creatorVerificationMapper.updateById(verification);
    }
}
