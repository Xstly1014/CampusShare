package com.campushare.agent.prompt;

import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.mapper.PromptVersionMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * System Prompt 版本管理器。
 *
 * 职责：
 *  1. 读取当前生效版本（含灰度判断，按 user_id 哈希分流）
 *  2. 切换版本（秒级生效，写 Redis）
 *  3. 设置灰度比例（0-100）
 *  4. 回滚到上一版本（秒级）
 *
 * 缓存策略：
 *  - Redis 存当前版本号 + 灰度比例（key: agent:prompt:current_version / agent:prompt:gray_ratio）
 *  - DB 存完整 Prompt 内容（prompt_versions 表）
 *  - DB/Redis 故障时降级到 PromptConstants 硬编码常量（meter: prompt.version.fallback）
 *
 * 灰度规则（ADR-SP-07）：
 *  - 按 user_id 哈希取模 100，< grayRatio 用新版，>= grayRatio 用旧版
 *  - L1 platform_prompt 字节级固定（ADR-SP-06），灰度只切 L2/L3/L4
 */
@Slf4j
@Component
public class PromptVersionManager {

    private static final String CURRENT_VERSION_KEY = "agent:prompt:current_version";
    private static final String GRAY_RATIO_KEY = "agent:prompt:gray_ratio";

    private final StringRedisTemplate redis;
    private final PromptVersionMapper versionMapper;
    private final MeterRegistry meterRegistry;
    private final Counter fallbackCounter;

    /** 降级用的单例 PromptVersion（从 PromptConstants 构建，避免每请求 new）。 */
    private volatile PromptVersion fallbackVersion;

    @Value("${app.prompt.version.current:v1.0.0}")
    private String defaultCurrentVersion;

    @Value("${app.prompt.version.gray-ratio:100}")
    private int defaultGrayRatio;

    @Value("${app.prompt.version.fallback-on-db-error:true}")
    private boolean fallbackOnDbError;

    public PromptVersionManager(StringRedisTemplate redis,
                                PromptVersionMapper versionMapper,
                                MeterRegistry meterRegistry) {
        this.redis = redis;
        this.versionMapper = versionMapper;
        this.meterRegistry = meterRegistry;
        this.fallbackCounter = Counter.builder("prompt.version.fallback")
                .description("Number of times PromptVersionManager fell back to hardcoded constants")
                .register(meterRegistry);
    }

    @PostConstruct
    void init() {
        fallbackVersion = PromptVersion.builder()
                .version(PromptConstants.CURRENT_VERSION)
                .platformPrompt(PromptConstants.PLATFORM_PROMPT)
                .howToPrompt(PromptConstants.HOW_TO_PROMPT)
                .searchPrompt(PromptConstants.SEARCH_PROMPT)
                .chatPrompt(PromptConstants.CHAT_PROMPT)
                .fewShotPrompt(PromptConstants.FEW_SHOT_PROMPT)
                .guardrailPrompt(PromptConstants.GUARDRAIL_PROMPT)
                .status("RELEASED")
                .grayRatio(100)
                .build();

        if (Boolean.FALSE.equals(redis.hasKey(CURRENT_VERSION_KEY))) {
            redis.opsForValue().set(CURRENT_VERSION_KEY, defaultCurrentVersion);
            redis.opsForValue().set(GRAY_RATIO_KEY, String.valueOf(defaultGrayRatio));
            log.info("Initialized prompt version in Redis: version={}, grayRatio={}",
                    defaultCurrentVersion, defaultGrayRatio);
        }
    }

    /**
     * 获取当前生效版本（含灰度判断）。
     *
     * @param userId 用户ID（用于灰度哈希）
     * @return PromptVersion（永不返回 null，DB 故障时返回降级单例）
     */
    public PromptVersion getCurrentVersion(String userId) {
        try {
            String currentVersion = redis.opsForValue().get(CURRENT_VERSION_KEY);
            String grayRatioStr = redis.opsForValue().get(GRAY_RATIO_KEY);

            if (currentVersion == null) {
                log.warn("Redis missing current version key, using default: {}", defaultCurrentVersion);
                currentVersion = defaultCurrentVersion;
            }

            int grayRatio = grayRatioStr != null ? Integer.parseInt(grayRatioStr) : 100;

            PromptVersion current = versionMapper.findByVersion(currentVersion);
            if (current == null) {
                log.warn("Version {} not found in DB, falling back to constants", currentVersion);
                fallbackCounter.increment();
                return fallbackVersion;
            }

            if (grayRatio < 100 && userId != null) {
                int userHash = Math.abs(userId.hashCode() % 100);
                if (userHash >= grayRatio) {
                    PromptVersion previous = versionMapper.findPreviousReleased(currentVersion);
                    if (previous != null) {
                        return previous;
                    }
                }
            }

            return current;
        } catch (Exception e) {
            log.error("Failed to get current prompt version, falling back to constants", e);
            fallbackCounter.increment();
            return fallbackVersion;
        }
    }

    /**
     * 切换版本（秒级生效）。
     *
     * @param newVersion 新版本号（必须已在 DB 中存在）
     */
    public void switchVersion(String newVersion) {
        PromptVersion version = versionMapper.findByVersion(newVersion);
        if (version == null) {
            throw new IllegalArgumentException("Version not found: " + newVersion);
        }

        version.setStatus("RELEASED");
        version.setReleasedAt(LocalDateTime.now());
        versionMapper.updateById(version);

        redis.opsForValue().set(CURRENT_VERSION_KEY, newVersion);
        redis.opsForValue().set(GRAY_RATIO_KEY, "100");

        log.info("Switched prompt version to: {}", newVersion);
    }

    /**
     * 设置灰度比例（0-100）。
     */
    public void setGrayRatio(int ratio) {
        if (ratio < 0 || ratio > 100) {
            throw new IllegalArgumentException("Gray ratio must be 0-100, got: " + ratio);
        }
        redis.opsForValue().set(GRAY_RATIO_KEY, String.valueOf(ratio));
        log.info("Set gray ratio to: {}", ratio);
    }

    /**
     * 回滚到上一 RELEASED 版本（秒级）。
     */
    public void rollback() {
        String current = redis.opsForValue().get(CURRENT_VERSION_KEY);
        if (current == null) {
            throw new IllegalStateException("No current version to rollback from");
        }

        PromptVersion previous = versionMapper.findPreviousReleased(current);
        if (previous == null) {
            throw new IllegalStateException("No previous released version to rollback to");
        }

        redis.opsForValue().set(CURRENT_VERSION_KEY, previous.getVersion());
        redis.opsForValue().set(GRAY_RATIO_KEY, "100");

        log.info("Rolled back from {} to {}", current, previous.getVersion());
    }
}
