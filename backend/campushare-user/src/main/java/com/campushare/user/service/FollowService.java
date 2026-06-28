package com.campushare.user.service;

import com.campushare.user.dto.UserDTO;

import java.util.List;
import java.util.Map;

public interface FollowService {

    boolean toggleFollow(String currentUserId, String targetUserId);

    boolean isFollowing(String followerId, String followingId);

    long getFollowerCount(String userId);

    long getFollowingCount(String userId);

    Map<String, Long> getFollowStats(String userId);

    List<UserDTO> getFollowingList(String userId);

    List<UserDTO> getFollowerList(String userId);

    List<UserDTO> getMutualList(String userId);
}
