package com.campushare.user.service;

import com.campushare.user.dto.CommentDTO;

import java.util.List;

public interface CommentService {

    /** Create a comment on a post */
    CommentDTO createComment(String userId, String postId, String content, String parentId, String replyToUserId);

    /** Get comments for a post, ordered by create_time asc */
    List<CommentDTO> getCommentsByPostId(String postId);
}
