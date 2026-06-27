package com.campushare.user.service;

import com.campushare.user.dto.CommentDTO;

import java.util.List;
import java.util.Map;

public interface CommentService {

    /** Create a comment on a post */
    CommentDTO createComment(String userId, String postId, String content, String parentId, String replyToUserId);

    /** Get comments for a post, ordered by create_time asc */
    List<CommentDTO> getCommentsByPostId(String postId);

    /** Get all comments by a user, ordered by create_time desc */
    List<CommentDTO> getCommentsByUserId(String userId);

    /** Delete a comment (only by author), decrement post comment_count */
    void deleteComment(String userId, String commentId);

    /** Toggle like on a comment, returns true if liked after toggle */
    boolean toggleCommentLike(String userId, String commentId);

    /** Check if user has liked a comment */
    boolean isCommentLikedBy(String userId, String commentId);

    /** Get liked status for a batch of comments by a user */
    Map<String, Boolean> getCommentLikeStatuses(String userId, List<String> commentIds);
}
