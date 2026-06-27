package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.CommentDTO;
import com.campushare.user.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final JwtUtils jwtUtils;

    @GetMapping("/{postId}/comments")
    public Result<List<CommentDTO>> getComments(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String postId) {
        List<CommentDTO> comments = commentService.getCommentsByPostId(postId);
        // Attach like statuses if logged in
        String userId = extractUserId(token);
        if (userId != null && !comments.isEmpty()) {
            List<String> commentIds = comments.stream().map(CommentDTO::getId).toList();
            Map<String, Boolean> statuses = commentService.getCommentLikeStatuses(userId, commentIds);
            for (CommentDTO c : comments) {
                c.setLiked(statuses.getOrDefault(c.getId(), false));
                c.setIsAuthor(c.getUserId().equals(userId));
            }
        } else {
            for (CommentDTO c : comments) {
                c.setLiked(false);
                c.setIsAuthor(false);
            }
        }
        return Result.success(comments);
    }

    @GetMapping("/my-comments")
    public Result<List<CommentDTO>> getMyComments(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        List<CommentDTO> comments = commentService.getCommentsByUserId(userId);
        return Result.success(comments);
    }

    @PostMapping("/{postId}/comments")
    public Result<CommentDTO> createComment(
            @RequestHeader("Authorization") String token,
            @PathVariable String postId,
            @RequestBody Map<String, String> body) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        String content = body.get("content");
        String parentId = body.get("parentId");
        String replyToUserId = body.get("replyToUserId");
        CommentDTO comment = commentService.createComment(userId, postId, content, parentId, replyToUserId);
        comment.setIsAuthor(true);
        comment.setLiked(false);
        return Result.success(comment);
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(
            @RequestHeader("Authorization") String token,
            @PathVariable String commentId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        commentService.deleteComment(userId, commentId);
        return Result.success(null);
    }

    @PostMapping("/comments/{commentId}/like")
    public Result<Boolean> toggleCommentLike(
            @RequestHeader("Authorization") String token,
            @PathVariable String commentId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        boolean isLiked = commentService.toggleCommentLike(userId, commentId);
        return Result.success(isLiked);
    }

    private String extractUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return jwtUtils.getUserId(token.replace("Bearer ", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
