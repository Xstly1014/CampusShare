package com.campushare.post.service.impl;

import com.campushare.post.entity.Post;
import com.campushare.post.feign.UserFeignClient;
import com.campushare.post.mapper.*;
import com.campushare.post.service.DataInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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
    private final PostDownloadMapper postDownloadMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserFeignClient userFeignClient;

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
            "离散数学", "编译原理", "设计模式", "算法导论", "微服务架构",
            "食堂推荐", "宿舍条件", "选课攻略", "社团招新", "实习经验",
            "保研经验", "求职分享", "校园风景", "运动会", "学生会"
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
            "这门课的重点总结，总共就十几页纸，背完考试稳了。",
            "学校食堂新开的窗口味道还不错，推荐大家去试试。",
            "宿舍改造完成，花了不到200块，效果超满意。",
            "这家公司实习体验很好，氛围轻松，工资也不错，推荐给学弟学妹。",
            "保研成功了，分享一下准备过程中的经验和踩过的坑。",
            "秋招拿到了心仪的offer，总结一下面试经验。"
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

        postDownloadMapper.deleteAllPhysical();
        log.info("post_downloads 表已清空");

        postMapper.deleteAllPhysical();
        log.info("posts 表已清空");

        clearRedisKeys("post:view:*");
        clearRedisKeys("post:star:*");
        clearRedisKeys("post:like:*");

        String result = "所有帖子及相关数据已清空（posts, comments, post_stars, post_likes, comment_likes, view_history, post_downloads, Redis缓存）";
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
                Post post = buildRandomPost(schoolId, generateRandomUserId());
                posts.add(post);
                totalPosts++;

                if (posts.size() >= 100) {
                    batchInsertPosts(posts);
                    posts.clear();
                    log.info("学校 {} 已插入 {} 条", schoolId, totalPosts);
                }
            }

            if (!posts.isEmpty()) {
                batchInsertPosts(posts);
            }
            log.info("学校 {} 数据生成完成", schoolId);
        }

        String result = String.format("测试数据生成完成！共生成 %d 条帖子", totalPosts);
        log.info(result);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String initFullTestData(int userCount, int postsPerUser) {
        log.info("========== 开始全量数据初始化: {} 用户, 每用户约 {} 帖 ==========", userCount, postsPerUser);

        int batchSize = 500;
        int totalUsers = 0;
        int totalPosts = 0;
        int totalBatches = (int) Math.ceil((double) userCount / batchSize);

        for (int batch = 0; batch < totalBatches; batch++) {
            int currentBatchSize = Math.min(batchSize, userCount - batch * batchSize);
            log.info("===== 处理第 {}/{} 批，创建 {} 个用户 =====", batch + 1, totalBatches, currentBatchSize);

            List<Map<String, String>> users;
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("count", currentBatchSize);
                Object response = userFeignClient.batchCreateTestUsers(request);

                if (response instanceof Map) {
                    Map<String, Object> respMap = (Map<String, Object>) response;
                    Object data = respMap.get("data");
                    if (data instanceof List) {
                        users = (List<Map<String, String>>) data;
                    } else {
                        log.error("Feign返回数据格式异常: {}", respMap);
                        continue;
                    }
                } else {
                    log.error("Feign返回类型异常: {}", response);
                    continue;
                }
            } catch (Exception e) {
                log.error("第 {} 批创建用户失败: {}", batch + 1, e.getMessage(), e);
                continue;
            }

            totalUsers += users.size();
            log.info("第 {} 批用户创建成功: {} 个", batch + 1, users.size());

            List<Post> posts = new ArrayList<>();
            for (Map<String, String> userInfo : users) {
                String userId = userInfo.get("id");
                String schoolId = userInfo.get("schoolId");
                if (userId == null || schoolId == null) continue;

                int userPostCount = postsPerUser + random.nextInt(3) - 1;
                if (userPostCount < 1) userPostCount = 1;

                for (int p = 0; p < userPostCount; p++) {
                    posts.add(buildRandomPost(schoolId, userId));
                    totalPosts++;

                    if (posts.size() >= 200) {
                        batchInsertPosts(posts);
                        posts.clear();
                    }
                }
            }

            if (!posts.isEmpty()) {
                batchInsertPosts(posts);
            }

            log.info("第 {} 批完成，累计: {} 用户, {} 帖子", batch + 1, totalUsers, totalPosts);
        }

        String result = String.format("全量数据初始化完成！共创建 %d 个用户，%d 条帖子（分布在 %d 个学校）",
                totalUsers, totalPosts, SCHOOL_IDS.length);
        log.info(result);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String initTargetedSchoolData(int userCount, int postsPerUser, String schoolId) {
        log.info("========== 开始精准数据初始化: {} 用户 × {} 帖/人, 学校={} ==========", userCount, postsPerUser, schoolId);

        int batchSize = 200;
        int totalUsers = 0;
        int totalPosts = 0;
        int totalBatches = (int) Math.ceil((double) userCount / batchSize);

        for (int batch = 0; batch < totalBatches; batch++) {
            int currentBatchSize = Math.min(batchSize, userCount - batch * batchSize);
            log.info("===== 处理第 {}/{} 批，创建 {} 个用户(学校={}) =====", batch + 1, totalBatches, currentBatchSize, schoolId);

            List<Map<String, String>> users;
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("count", currentBatchSize);
                request.put("schoolId", schoolId);
                Object response = userFeignClient.batchCreateTestUsers(request);

                if (response instanceof Map) {
                    Map<String, Object> respMap = (Map<String, Object>) response;
                    Object data = respMap.get("data");
                    if (data instanceof List) {
                        users = (List<Map<String, String>>) data;
                    } else {
                        log.error("Feign返回数据格式异常: {}", respMap);
                        continue;
                    }
                } else {
                    log.error("Feign返回类型异常: {}", response);
                    continue;
                }
            } catch (Exception e) {
                log.error("第 {} 批创建用户失败: {}", batch + 1, e.getMessage(), e);
                continue;
            }

            totalUsers += users.size();
            log.info("第 {} 批用户创建成功: {} 个", batch + 1, users.size());

            List<Post> posts = new ArrayList<>();
            for (Map<String, String> userInfo : users) {
                String userId = userInfo.get("id");
                if (userId == null) continue;

                for (int p = 0; p < postsPerUser; p++) {
                    posts.add(buildRandomPost(schoolId, userId));
                    totalPosts++;

                    if (posts.size() >= 200) {
                        batchInsertPosts(posts);
                        posts.clear();
                    }
                }
            }

            if (!posts.isEmpty()) {
                batchInsertPosts(posts);
            }

            log.info("第 {} 批完成，累计: {} 用户, {} 帖子", batch + 1, totalUsers, totalPosts);
        }

        String result = String.format("精准数据初始化完成！学校=%s，共创建 %d 个用户，%d 条帖子",
                schoolId, totalUsers, totalPosts);
        log.info(result);
        return result;
    }

    private Post buildRandomPost(String schoolId, String authorId) {
        String postType = getRandomPostType();
        String title = generateTitle();
        String content = generateContent();
        LocalDateTime randomTime = generateRandomTime();

        return Post.builder()
                .schoolId(schoolId)
                .authorId(authorId)
                .postType(postType)
                .title(title)
                .content(content)
                .viewCount(random.nextInt(500))
                .starCount(0)
                .likeCount(random.nextInt(50))
                .commentCount(0)
                .status(1)
                .deleted(false)
                .createTime(randomTime)
                .updateTime(randomTime)
                .build();
    }

    private void batchInsertPosts(List<Post> posts) {
        for (Post p : posts) {
            try {
                postMapper.insert(p);
            } catch (Exception e) {
                log.error("插入帖子失败: title={}", p.getTitle(), e);
            }
        }
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

    @Override
    public String initCreatorTestData(String userId, String schoolId) {
        log.info("========== 开始为用户 {} 初始化创作者测试数据（学校={}） ==========", userId, schoolId);

        int postCount = 60;
        int minLikesPerPost = 180;
        int maxLikesPerPost = 220;
        List<Post> posts = new ArrayList<>();

        for (int i = 0; i < postCount; i++) {
            String postType = getRandomPostType();
            String title = generateTitle();
            String content = generateContent();
            LocalDateTime randomTime = generateRandomTime();
            int likeCount = minLikesPerPost + random.nextInt(maxLikesPerPost - minLikesPerPost + 1);
            int viewCount = likeCount * 5 + random.nextInt(200);

            Post post = Post.builder()
                    .schoolId(schoolId)
                    .authorId(userId)
                    .postType(postType)
                    .title(title)
                    .content(content)
                    .viewCount(viewCount)
                    .starCount(random.nextInt(50))
                    .likeCount(likeCount)
                    .commentCount(random.nextInt(20))
                    .status(1)
                    .deleted(false)
                    .createTime(randomTime)
                    .updateTime(randomTime)
                    .build();
            posts.add(post);
        }

        batchInsertPosts(posts);

        long totalLikes = posts.stream().mapToLong(Post::getLikeCount).sum();
        long totalViews = posts.stream().mapToLong(Post::getViewCount).sum();

        String result = String.format("创作者测试数据初始化完成：用户ID=%s，共创建%d篇帖子，总获赞%d，总浏览%d（满足：帖≥50，赞≥10000）",
                userId, postCount, totalLikes, totalViews);
        log.info(result);
        return result;
    }

    @Override
    public String fillEmptyPostContent() {
        log.info("开始补全空内容帖子...");
        List<Post> emptyPosts = postMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                        .and(w -> w.isNull(Post::getContent).or().eq(Post::getContent, ""))
                        .eq(Post::getDeleted, false)
        );
        int count = 0;
        for (Post p : emptyPosts) {
            p.setContent(generateContent());
            postMapper.updateById(p);
            count++;
            if (count % 100 == 0) {
                log.info("已补全 {} 篇帖子", count);
            }
        }
        String result = String.format("空内容补全完成：共更新 %d 篇帖子", count);
        log.info(result);
        return result;
    }
}
