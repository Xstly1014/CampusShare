package com.campushare.user.service;

import com.campushare.user.dto.CreatePostRequest;
import com.campushare.user.dto.UserPostStats;
import com.campushare.user.entity.Post;

import java.util.List;

public interface PostService {

    Post createPost(String userId, CreatePostRequest request);

    Post getPostById(String postId);

    List<Post> getPostsBySchool(String schoolId, String postType, String sortType, int page, int size);

    void incrementViewCount(String userId, String postId);

    boolean toggleStar(String userId, String postId);

    boolean toggleLike(String userId, String postId);

    /** Check if a user has starred a post (for display state) */
    boolean isStarredBy(String userId, String postId);

    /** Check if a user has liked a post (for display state) */
    boolean isLikedBy(String userId, String postId);

    /** Get user's browse history, ordered by most recent view time */
    List<Post> getViewHistory(String userId, int page, int size);

    /** Get user's starred posts */
    List<Post> getStarredPosts(String userId, int page, int size);

    /** Get user's liked posts */
    List<Post> getLikedPosts(String userId, int page, int size);

    /** Get posts authored by the user */
    List<Post> getMyPosts(String userId, int page, int size);

    /** Get aggregate stats of a user's posts: total views, total likes, total stars */
    UserPostStats getMyPostStats(String userId);

    /** Get post count for each school */
    java.util.Map<String, Long> getSchoolPostCounts();
}
