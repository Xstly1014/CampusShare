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
    public Result<List<CommentDTO>> getComments(@PathVariable String postId) {
        List<CommentDTO> comments = commentService.getCommentsByPostId(postId);
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
        return Result.success(comment);
    }
}
