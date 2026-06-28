package com.campushare.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.post.dto.CommentDTO;
import com.campushare.post.entity.Comment;
import com.campushare.post.entity.CommentLike;
import com.campushare.post.entity.Post;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.CommentLikeMapper;
import com.campushare.post.mapper.CommentMapper;
import com.campushare.post.mapper.PostMapper;
import com.campushare.post.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional
    public CommentDTO createComment(String userId, String postId, String content, String parentId,
            String replyToUserId) {
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

        String postTitle = post.getTitle();
        if (postTitle == null || postTitle.isEmpty()) {
            postTitle = post.getContent().length() > 20 ? post.getContent().substring(0, 20) + "..."
                    : post.getContent();
        }

        try {
            if (replyToUserId != null && !replyToUserId.isEmpty() && !replyToUserId.equals(userId)) {
                UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                req.setUserId(replyToUserId);
                req.setSenderId(userId);
                req.setType("REPLY");
                req.setTargetId(postId);
                req.setTargetTitle(postTitle);
                userFeignClient.createNotification(req);
            } else if (!post.getAuthorId().equals(userId)) {
                UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                req.setUserId(post.getAuthorId());
                req.setSenderId(userId);
                req.setType("COMMENT");
                req.setTargetId(postId);
                req.setTargetTitle(postTitle);
                userFeignClient.createNotification(req);
            }
        } catch (Exception e) {
            log.warn("Feign调用创建评论通知失败: {}", e.getMessage());
        }

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
        if (!comment.getUserId().equals(userId)) {
            Post post = postMapper.selectById(comment.getPostId());
            if (post == null || !post.getAuthorId().equals(userId)) {
                throw new BusinessException(4030, "无权删除他人的评论");
            }
        }

        commentMapper.deleteById(commentId);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));

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
            try {
                if (!comment.getUserId().equals(userId)) {
                    String commentContent = comment.getContent();
                    String targetTitle = commentContent.length() > 20 ? commentContent.substring(0, 20) + "..." : commentContent;
                    UserFeignClient.NotificationRequest req = new UserFeignClient.NotificationRequest();
                    req.setUserId(comment.getUserId());
                    req.setSenderId(userId);
                    req.setType("COMMENT_LIKE");
                    req.setTargetId(comment.getPostId());
                    req.setTargetTitle(targetTitle);
                    userFeignClient.createNotification(req);
                }
            } catch (Exception e) {
                log.warn("Feign调用创建评论点赞通知失败: {}", e.getMessage());
            }
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
        String username = "未知用户";
        String avatarUrl = null;
        String replyToUsername = null;

        try {
            List<String> userIds = new ArrayList<>();
            userIds.add(comment.getUserId());
            if (comment.getReplyToUserId() != null && !comment.getReplyToUserId().isEmpty()) {
                userIds.add(comment.getReplyToUserId());
            }
            List<UserFeignClient.UserSimpleInfo> users = userFeignClient.getBatchUserInfo(userIds);
            Map<String, UserFeignClient.UserSimpleInfo> userMap = new HashMap<>();
            if (users != null) {
                for (UserFeignClient.UserSimpleInfo u : users) {
                    userMap.put(u.getId(), u);
                }
            }
            UserFeignClient.UserSimpleInfo commentUser = userMap.get(comment.getUserId());
            if (commentUser != null) {
                username = commentUser.getUsername();
                avatarUrl = commentUser.getAvatarUrl();
            }
            if (comment.getReplyToUserId() != null && !comment.getReplyToUserId().isEmpty()) {
                UserFeignClient.UserSimpleInfo replyUser = userMap.get(comment.getReplyToUserId());
                if (replyUser != null) {
                    replyToUsername = replyUser.getUsername();
                }
            }
        } catch (Exception e) {
            log.warn("Feign调用获取用户信息失败: {}", e.getMessage());
        }

        if (avatarUrl == null) {
            avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + comment.getUserId();
        }

        Post post = postMapper.selectById(comment.getPostId());
        String schoolId = post != null ? post.getSchoolId() : null;

        return CommentDTO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .schoolId(schoolId)
                .userId(comment.getUserId())
                .username(username)
                .avatarUrl(avatarUrl)
                .parentId(comment.getParentId())
                .replyToUserId(comment.getReplyToUserId())
                .replyToUsername(replyToUsername)
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .build();
    }
}
