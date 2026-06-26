package com.campushare.user.service;

import com.campushare.user.dto.CreatePostRequest;
import com.campushare.user.entity.Post;

import java.util.List;

public interface PostService {

    Post createPost(String userId, CreatePostRequest request);

    Post getPostById(String postId);

    List<Post> getPostsBySchool(String schoolId, String postType, String sortType, int page, int size);

    void incrementViewCount(String postId);

    boolean toggleStar(String userId, String postId);

    boolean toggleLike(String userId, String postId);
}
