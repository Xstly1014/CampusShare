package com.campushare.post.service;

import com.campushare.post.dto.*;
import com.campushare.post.entity.Post;

import java.util.List;
import java.util.Map;

public interface PostService {

    Post createPost(String userId, CreatePostRequest request);

    Post editPost(String userId, String postId, CreatePostRequest request);

    void deletePost(String userId, String postId);

    Post getPostById(String postId);

    List<Post> getPostsBySchool(String schoolId, String postType, String sortType, int page, int size);

    void incrementViewCount(String userId, String postId);

    boolean toggleStar(String userId, String postId);

    boolean toggleLike(String userId, String postId);

    boolean isStarredBy(String userId, String postId);

    boolean isLikedBy(String userId, String postId);

    List<Post> getViewHistory(String userId, int page, int size);

    List<Post> getStarredPosts(String userId, int page, int size);

    List<Post> getLikedPosts(String userId, int page, int size);

    List<Post> getMyPosts(String userId, int page, int size);

    UserPostStats getMyPostStats(String userId);

    Map<String, Long> getSchoolPostCounts();

    UserPostStats getUserPostStats(String userId);
}
