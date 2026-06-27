package com.campushare.user.service.impl;

import com.campushare.user.entity.Post;
import com.campushare.user.mapper.PostMapper;
import com.campushare.user.service.DataInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitServiceImpl implements DataInitService {

    private final PostMapper postMapper;

    @Value("${file.upload-path}")
    private String uploadPath;

    private static final String[] SCHOOL_IDS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"
    };

    private static final String[] POST_TYPES = {"resource", "discussion", "note"};

    private static final String[] FILE_TYPES = {"pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "zip", "txt", "jpg", "png"};

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

    /** Maximum size for dummy test files: 1KB (avoids OOM when generating 6000+ files) */
    private static final int MAX_DUMMY_FILE_SIZE = 1024;

    @Override
    public String initTestData(int postsPerSchool) {
        int totalPosts = 0;
        int totalFiles = 0;

        for (String schoolId : SCHOOL_IDS) {
            log.info("开始生成学校 {} 的测试数据，目标 {} 条", schoolId, postsPerSchool);
            List<Post> posts = new ArrayList<>();

            for (int i = 0; i < postsPerSchool; i++) {
                String postType = getRandomPostType();
                String title = generateTitle();
                String content = generateContent();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime randomTime = generateRandomTime();

                Post post = new Post();
                post.setSchoolId(schoolId);
                post.setAuthorId(generateRandomUserId());
                post.setPostType(postType);
                post.setTitle(title);
                post.setContent(content);
                post.setViewCount(random.nextInt(5000));
                post.setStarCount(random.nextInt(200));
                post.setLikeCount(random.nextInt(500));
                post.setCommentCount(random.nextInt(100));
                post.setStatus(1);
                post.setCreateTime(randomTime);
                post.setUpdateTime(randomTime);

                if ("resource".equals(postType) && random.nextDouble() < 0.8) {
                    String fileType = getRandomFileType();
                    String fileName = generateFileName(title, fileType);
                    String fileUrl = generateFileAndGetUrl(fileName, fileType);
                    if (fileUrl != null) {
                        post.setFileUrl(fileUrl);
                        post.setFileName(fileName);
                        post.setFileType(fileType);
                        post.setFileSize((long) MAX_DUMMY_FILE_SIZE);
                        totalFiles++;
                    }
                }

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

        String result = String.format("测试数据生成完成！共生成 %d 条帖子，%d 个文件", totalPosts, totalFiles);
        log.info(result);
        return result;
    }

    private String getRandomPostType() {
        double r = random.nextDouble();
        if (r < 0.5) return "resource";
        if (r < 0.85) return "discussion";
        return "note";
    }

    private String getRandomFileType() {
        return FILE_TYPES[random.nextInt(FILE_TYPES.length)];
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

    private String generateFileName(String title, String fileType) {
        String cleanTitle = title.replaceAll("[【】\\s]", "");
        int maxLen = Math.min(20, cleanTitle.length());
        cleanTitle = cleanTitle.substring(0, maxLen);
        return cleanTitle + "." + fileType;
    }

    private String generateFileAndGetUrl(String fileName, String fileType) {
        try {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path dirPath = Paths.get(uploadPath, dateStr);
            Files.createDirectories(dirPath);

            String uuid = UUID.randomUUID().toString().replace("-", "");
            String storedName = uuid + "." + fileType;
            Path filePath = dirPath.resolve(storedName);

            byte[] dummyContent = generateDummyFileContent(fileType);
            Files.write(filePath, dummyContent);

            return "/files/" + dateStr + "/" + storedName;
        } catch (IOException e) {
            log.error("生成测试文件失败: fileName={}", fileName, e);
            return null;
        }
    }

    private byte[] generateDummyFileContent(String fileType) {
        byte[] data = new byte[MAX_DUMMY_FILE_SIZE];

        switch (fileType) {
            case "pdf":
                byte[] pdfHeader = "%PDF-1.4\n%dummy pdf\n".getBytes();
                System.arraycopy(pdfHeader, 0, data, 0, Math.min(pdfHeader.length, data.length));
                for (int i = pdfHeader.length; i < data.length; i++) {
                    data[i] = (byte) (random.nextInt(26) + 'a');
                }
                break;
            case "txt":
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) (random.nextInt(26) + 'a');
                    if (i % 80 == 0) data[i] = '\n';
                }
                break;
            default:
                random.nextBytes(data);
        }

        return data;
    }
}
