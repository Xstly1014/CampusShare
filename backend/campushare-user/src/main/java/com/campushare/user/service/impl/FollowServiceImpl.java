package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.user.dto.UserDTO;
import com.campushare.user.entity.Follow;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.FollowMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CreatorService;
import com.campushare.user.service.FollowService;
import com.campushare.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final CreatorService creatorService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public boolean toggleFollow(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            return false;
        }
        Follow existing = followMapper.selectOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, currentUserId)
                        .eq(Follow::getFollowingId, targetUserId));
        if (existing != null) {
            followMapper.deleteById(existing.getId());
            return false;
        } else {
            Follow follow = Follow.builder()
                    .followerId(currentUserId)
                    .followingId(targetUserId)
                    .build();
            followMapper.insert(follow);
            notificationService.createNotification(targetUserId, currentUserId, "FOLLOW", null, null);
            return true;
        }
    }

    @Override
    public boolean isFollowing(String followerId, String followingId) {
        return followMapper.exists(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, followerId)
                        .eq(Follow::getFollowingId, followingId));
    }

    @Override
    public long getFollowerCount(String userId) {
        return followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
    }

    @Override
    public long getFollowingCount(String userId) {
        return followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
    }

    @Override
    public Map<String, Long> getFollowStats(String userId) {
        long followingCount = getFollowingCount(userId);
        long followerCount = getFollowerCount(userId);

        List<Follow> myFollowings = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        long mutualCount = 0;
        for (Follow f : myFollowings) {
            if (isFollowing(f.getFollowingId(), userId)) {
                mutualCount++;
            }
        }

        Map<String, Long> stats = new HashMap<>();
        stats.put("following", followingCount);
        stats.put("followers", followerCount);
        stats.put("mutual", mutualCount);
        return stats;
    }

    @Override
    public List<UserDTO> getFollowingList(String userId) {
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        return buildUserDTOsFromFollows(follows, true);
    }

    @Override
    public List<UserDTO> getFollowerList(String userId) {
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
        return buildUserDTOsFromFollows(follows, false);
    }

    @Override
    public List<UserDTO> getMutualList(String userId) {
        List<Follow> myFollowings = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        List<UserDTO> result = new ArrayList<>();
        for (Follow f : myFollowings) {
            if (isFollowing(f.getFollowingId(), userId)) {
                User u = userMapper.selectById(f.getFollowingId());
                if (u != null && !u.getDeleted()) {
                    result.add(convertToUserDTO(u));
                }
            }
        }
        return result;
    }

    private List<UserDTO> buildUserDTOsFromFollows(List<Follow> follows, boolean isFollowing) {
        List<UserDTO> result = new ArrayList<>();
        for (Follow f : follows) {
            String targetId = isFollowing ? f.getFollowingId() : f.getFollowerId();
            User u = userMapper.selectById(targetId);
            if (u != null && !u.getDeleted()) {
                result.add(convertToUserDTO(u));
            }
        }
        return result;
    }

    private UserDTO convertToUserDTO(User u) {
        UserDTO dto = new UserDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setAvatarUrl(u.getAvatarUrl());
        dto.setBio(u.getBio());
        dto.setCreator(creatorService.isCreator(u.getId()));
        dto.setAdmin(creatorService.isAdmin(u.getId()));
        return dto;
    }
}
