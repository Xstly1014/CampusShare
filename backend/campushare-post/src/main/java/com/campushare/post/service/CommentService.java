package com.campushare.post.service;

import com.campushare.post.dto.CommentDTO;

import java.util.List;
import java.util.Map;

public interface CommentService {

    CommentDTO createComment(String userId, String postId, String content, String parentId, String replyToUserId);

    List<CommentDTO> getCommentsByPostId(String postId);

    List<CommentDTO> getCommentsByUserId(String userId);

    void deleteComment(String userId, String commentId);

    boolean toggleCommentLike(String userId, String commentId);

    boolean isCommentLikedBy(String userId, String commentId);

    Map<String, Boolean> getCommentLikeStatuses(String userId, List<String> commentIds);
}
