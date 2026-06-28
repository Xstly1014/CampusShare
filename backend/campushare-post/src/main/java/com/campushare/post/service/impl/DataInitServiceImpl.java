package com.campushare.post.service.impl;

import com.campushare.post.entity.Post;
import com.campushare.post.mapper.*;
import com.campushare.post.service.DataInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitServiceImpl implements DataInitService {

    private final PostMapper postMapper;
    private final PostStarMapper postStarMapper;
    private final PostLikeMapper postLikeMapper;
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final ViewHistoryMapper viewHistoryMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String[] SCHOOL_IDS = {"1", "2", "3", "4", "5", "6", "7", "8"};
    private static final String[] POST_TYPES = {"resource", "discussion"};

    private static final String[] TITLE_PREFIXES = {
            "【期末复习】", "【考研资料】", "【课程笔记】", "【实验报告】", "【作业答案】",
            "【历年真题】", "【学习总结】", "【重点整理】", "【思维导图】", "【案例分析】",
            "讨论一下", "请教大家", "分享一个", "关于", "有没有人知道",
            "求助", "推荐", "经验分享", "避雷", "吐槽"
    };

    private static final String[] TITLE_TOPICS = {
            "高等数学", "线性代数", "概率论", "大学物理", "计算机组成原理",
            "数据结构", "操作系统", "计算机网络", "数据库原理", "软件工程",
            "C语言程序设计", "Java编程", "Python入门", "机器学习", "深度学习",
            "人工智能", "英语四级", "英语六级", "考研英语", "考研政治",
            "马克思主义原理", "毛泽东思想概论", "中国近代史纲要", "思想道德修养", "大学语文",
            "离散数学", "编译原理", "设计模式", "算法导论", "微服务架构"
    };

    private static final String[] CONTENT_SNIPPETS = {
            "这份资料是我整理了一个学期的心血，涵盖了所有重点知识点，希望对大家有帮助。",
            "去年考试的真题，亲测很多原题都考到了，建议大家认真做一遍。",
            "上课的时候记的笔记，可能有点乱，但是重点都标出来了，大家将就着看。",
            "实验报告完整版本，包括代码和运行结果，仅供参考，不要直接抄哦。",
            "作业的参考答案，自己做的，可能有错，欢迎大家指正。",
            "思维导图形式的知识点整理，一目了然，适合期末快速复习用。",
            "有没有大佬知道这道题怎么解？想了半天都没想出来，求指点。",
            "分享一下我的学习方法，亲测有效，期末轻松90+。",
            "这个老师的课真的太良心了，讲得特别清楚，推荐大家选。",
            "避雷！这门课作业巨多，考试还难，能不选就别选。",
            "今天考完了，感觉还行，把记得的题目分享给大家。",
            "求推荐一本好书，想深入学习一下这方面的内容。",
            "刚考完研，把我用过的资料都整理出来了，免费分享给学弟学妹。",
            "课程设计的完整代码和报告，花了我整整两周才做完。",
            "这门课的重点总结，总共就十几页纸，背完考试稳了。"
    };

    private static final Random random = new Random();

    @Override
    public String clearAllPosts() {
        log.info("开始清空所有帖子及相关数据...");

        commentLikeMapper.deleteAllPhysical();
        log.info("comment_likes 表已清空");

        commentMapper.deleteAllPhysical();
        log.info("comments 表已清空");

        postStarMapper.deleteAllPhysical();
        log.info("post_stars 表已清空");

        postLikeMapper.deleteAllPhysical();
        log.info("post_likes 表已清空");

        viewHistoryMapper.deleteAllPhysical();
        log.info("view_history 表已清空");

        postMapper.deleteAllPhysical();
        log.info("posts 表已清空");

        clearRedisKeys("post:view:*");
        clearRedisKeys("post:star:*");
        clearRedisKeys("post:like:*");

        String result = "所有帖子及相关数据已清空（posts, comments, post_stars, post_likes, comment_likes, view_history, Redis缓存）";
        log.info(result);
        return result;
    }

    private void clearRedisKeys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除 Redis 键: pattern={}, count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("清除 Redis 键失败: pattern={}", pattern, e);
        }
    }

    @Override
    public String initTestData(int postsPerSchool) {
        int totalPosts = 0;

        for (String schoolId : SCHOOL_IDS) {
            log.info("开始生成学校 {} 的测试数据，目标 {} 条", schoolId, postsPerSchool);
            List<Post> posts = new ArrayList<>();

            for (int i = 0; i < postsPerSchool; i++) {
                String postType = getRandomPostType();
                String title = generateTitle();
                String content = generateContent();
                LocalDateTime randomTime = generateRandomTime();

                Post post = Post.builder()
                        .schoolId(schoolId)
                        .authorId(generateRandomUserId())
                        .postType(postType)
                        .title(title)
                        .content(content)
                        .viewCount(0)
                        .starCount(0)
                        .likeCount(0)
                        .commentCount(0)
                        .status(1)
                        .deleted(false)
                        .createTime(randomTime)
                        .updateTime(randomTime)
                        .build();

                posts.add(post);
                totalPosts++;

                if (posts.size() >= 100) {
                    for (Post p : posts) {
                        try {
                            postMapper.insert(p);
                        } catch (Exception e) {
                            log.error("插入帖子失败: title={}", p.getTitle(), e);
                        }
                    }
                    posts.clear();
                    log.info("学校 {} 已插入 {} 条", schoolId, totalPosts);
                }
            }

            if (!posts.isEmpty()) {
                for (Post p : posts) {
                    try {
                        postMapper.insert(p);
                    } catch (Exception e) {
                        log.error("插入帖子失败: title={}", p.getTitle(), e);
                    }
                }
            }
            log.info("学校 {} 数据生成完成", schoolId);
        }

        String result = String.format("测试数据生成完成！共生成 %d 条帖子", totalPosts);
        log.info(result);
        return result;
    }

    private String getRandomPostType() {
        return random.nextDouble() < 0.5 ? "resource" : "discussion";
    }

    private String generateTitle() {
        String prefix = TITLE_PREFIXES[random.nextInt(TITLE_PREFIXES.length)];
        String topic = TITLE_TOPICS[random.nextInt(TITLE_TOPICS.length)];
        String suffix = "";
        if (random.nextBoolean()) {
            String[] suffixes = {"完整版", "详细版", "最新版", "高清版", "整理版", "分享", "汇总"};
            suffix = suffixes[random.nextInt(suffixes.length)];
        }
        return prefix + topic + suffix;
    }

    private String generateContent() {
        int count = 2 + random.nextInt(4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(CONTENT_SNIPPETS[random.nextInt(CONTENT_SNIPPETS.length)]);
            if (i < count - 1) sb.append("\n\n");
        }
        return sb.toString();
    }

    private String generateRandomUserId() {
        long id = 10000 + random.nextInt(90000);
        return String.valueOf(id);
    }

    private LocalDateTime generateRandomTime() {
        LocalDateTime now = LocalDateTime.now();
        int daysBack = random.nextInt(180);
        int hoursBack = random.nextInt(24);
        int minutesBack = random.nextInt(60);
        return now.minusDays(daysBack).minusHours(hoursBack).minusMinutes(minutesBack);
    }
}
