package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.CommentDTO;
import com.campushare.user.entity.Comment;
import com.campushare.user.entity.Post;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.CommentMapper;
import com.campushare.user.mapper.PostMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public CommentDTO createComment(String userId, String postId, String content, String parentId, String replyToUserId) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(40001, "评论内容不能为空");
        }

        // Verify post exists
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted()) {
            throw new BusinessException(40004, "帖子不存在");
        }

        // Create comment
        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(parentId)
                .replyToUserId(replyToUserId)
                .content(content.trim())
                .likeCount(0)
                .status(1)
                .build();
        commentMapper.insert(comment);

        // Increment post comment_count atomically
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));

        log.info("用户 {} 评论帖子 {}", userId, postId);

        // Build DTO with user info
        return buildCommentDTO(comment);
    }

    @Override
    public List<CommentDTO> getCommentsByPostId(String postId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .eq(Comment::getDeleted, false)
                        .orderByAsc(Comment::getCreateTime));

        List<CommentDTO> result = new ArrayList<>();
        for (Comment c : comments) {
            result.add(buildCommentDTO(c));
        }
        return result;
    }

    @Override
    public List<CommentDTO> getCommentsByUserId(String userId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getStatus, 1)
                        .eq(Comment::getDeleted, false)
                        .orderByDesc(Comment::getCreateTime));

        List<CommentDTO> result = new ArrayList<>();
        for (Comment c : comments) {
            result.add(buildCommentDTO(c));
        }
        return result;
    }

    private CommentDTO buildCommentDTO(Comment comment) {
        User user = userMapper.selectById(comment.getUserId());
        return CommentDTO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .username(user != null ? user.getUsername() : "未知用户")
                .avatarUrl(user != null && user.getAvatarUrl() != null ? user.getAvatarUrl()
                        : "https://api.dicebear.com/7.x/avataaars/svg?seed=" + comment.getUserId())
                .parentId(comment.getParentId())
                .replyToUserId(comment.getReplyToUserId())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .build();
    }
}
