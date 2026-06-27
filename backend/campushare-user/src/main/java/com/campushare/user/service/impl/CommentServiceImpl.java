package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.user.dto.CommentDTO;
import com.campushare.user.entity.Comment;
import com.campushare.user.entity.CommentLike;
import com.campushare.user.entity.Post;
import com.campushare.user.entity.User;
import com.campushare.user.mapper.CommentLikeMapper;
import com.campushare.user.mapper.CommentMapper;
import com.campushare.user.mapper.PostMapper;
import com.campushare.user.mapper.UserMapper;
import com.campushare.user.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    @Transactional
    public CommentDTO createComment(String userId, String postId, String content, String parentId, String replyToUserId) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(40001, "评论内容不能为空");
        }

        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted()) {
            throw new BusinessException(40004, "帖子不存在");
        }

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

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));

        log.info("用户 {} 评论帖子 {}", userId, postId);
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

    @Override
    @Transactional
    public void deleteComment(String userId, String commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted()) {
            throw new BusinessException(40004, "评论不存在");
        }
        // Allow deletion by comment author OR post author
        if (!comment.getUserId().equals(userId)) {
            Post post = postMapper.selectById(comment.getPostId());
            if (post == null || !post.getAuthorId().equals(userId)) {
                throw new BusinessException(4030, "无权删除他人的评论");
            }
        }

        commentMapper.deleteById(commentId);

        // Decrement post comment_count
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));

        // Delete all likes for this comment
        commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, commentId));

        log.info("用户 {} 删除评论 {}", userId, commentId);
    }

    @Override
    @Transactional
    public boolean toggleCommentLike(String userId, String commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted()) {
            throw new BusinessException(40004, "评论不存在");
        }

        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));

        if (existing != null) {
            commentLikeMapper.deleteById(existing.getId());
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
            return false;
        } else {
            CommentLike like = CommentLike.builder()
                    .commentId(commentId)
                    .userId(userId)
                    .build();
            commentLikeMapper.insert(like);
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = like_count + 1"));
            return true;
        }
    }

    @Override
    public boolean isCommentLikedBy(String userId, String commentId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        Long count = commentLikeMapper.selectCount(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));
        return count != null && count > 0;
    }

    @Override
    public Map<String, Boolean> getCommentLikeStatuses(String userId, List<String> commentIds) {
        Map<String, Boolean> result = new HashMap<>();
        if (userId == null || userId.isEmpty() || commentIds.isEmpty()) {
            for (String id : commentIds) {
                result.put(id, false);
            }
            return result;
        }
        List<CommentLike> likes = commentLikeMapper.selectList(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getUserId, userId)
                        .in(CommentLike::getCommentId, commentIds));
        for (String id : commentIds) {
            result.put(id, false);
        }
        for (CommentLike like : likes) {
            result.put(like.getCommentId(), true);
        }
        return result;
    }

    private CommentDTO buildCommentDTO(Comment comment) {
        User user = userMapper.selectById(comment.getUserId());
        String replyToUsername = null;
        if (comment.getReplyToUserId() != null && !comment.getReplyToUserId().isEmpty()) {
            User replyToUser = userMapper.selectById(comment.getReplyToUserId());
            if (replyToUser != null) {
                replyToUsername = replyToUser.getUsername();
            }
        }
        // Get schoolId from post
        Post post = postMapper.selectById(comment.getPostId());
        String schoolId = post != null ? post.getSchoolId() : null;
        return CommentDTO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .schoolId(schoolId)
                .userId(comment.getUserId())
                .username(user != null ? user.getUsername() : "未知用户")
                .avatarUrl(user != null && user.getAvatarUrl() != null ? user.getAvatarUrl()
                        : "https://api.dicebear.com/7.x/avataaars/svg?seed=" + comment.getUserId())
                .parentId(comment.getParentId())
                .replyToUserId(comment.getReplyToUserId())
                .replyToUsername(replyToUsername)
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .build();
    }
}
