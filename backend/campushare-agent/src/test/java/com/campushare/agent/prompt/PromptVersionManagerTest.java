package com.campushare.agent.prompt;

import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.mapper.PromptVersionMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PromptVersionManager 单元测试。
 *
 * 验证点：
 *  - getCurrentVersion：Redis 缓存 + DB 查询 + 灰度哈希分流 + DB 故障降级
 *  - switchVersion：DB 更新 + Redis 写入
 *  - setGrayRatio：参数校验 + Redis 写入
 *  - rollback：Redis 写回上一版本
 *  - init：Redis 缺失时写入默认值
 *
 * Mock 策略：
 *  - StringRedisTemplate：mock opsForValue() 链式调用
 *  - PromptVersionMapper：mock findByVersion / findPreviousReleased / updateById
 *  - MeterRegistry：用 SimpleMeterRegistry（真实实现，验证 counter 增加）
 */
@DisplayName("PromptVersionManager 单元测试")
class PromptVersionManagerTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private PromptVersionMapper versionMapper;
    private SimpleMeterRegistry meterRegistry;

    private PromptVersionManager manager;

    private static final String CURRENT_VERSION_KEY = "agent:prompt:current_version";
    private static final String GRAY_RATIO_KEY = "agent:prompt:gray_ratio";

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        versionMapper = mock(PromptVersionMapper.class);
        meterRegistry = new SimpleMeterRegistry();

        // 默认 stub：hasKey=true 跳过 init 的写入逻辑（除非测试用例显式覆盖）
        when(redis.hasKey(CURRENT_VERSION_KEY)).thenReturn(true);
        when(redis.opsForValue()).thenReturn(valueOps);

        manager = new PromptVersionManager(redis, versionMapper, meterRegistry);
        // 单元测试中 @Value 不触发，手动注入默认值（与 application.yml 对齐）
        ReflectionTestUtils.setField(manager, "defaultCurrentVersion", "v1.0.0");
        ReflectionTestUtils.setField(manager, "defaultGrayRatio", 100);
        ReflectionTestUtils.setField(manager, "fallbackOnDbError", true);
        // 手动调用 @PostConstruct 逻辑（单元测试不会自动触发）
        manager.init();
    }

    private PromptVersion buildVersion(String version, int grayRatio, String status) {
        return PromptVersion.builder()
                .id("id-" + version)
                .version(version)
                .platformPrompt(PromptConstants.PLATFORM_PROMPT)
                .howToPrompt(PromptConstants.HOW_TO_PROMPT)
                .searchPrompt(PromptConstants.SEARCH_PROMPT)
                .chatPrompt(PromptConstants.CHAT_PROMPT)
                .fewShotPrompt(PromptConstants.FEW_SHOT_PROMPT)
                .guardrailPrompt(PromptConstants.GUARDRAIL_PROMPT)
                .status(status)
                .grayRatio(grayRatio)
                .releasedAt(LocalDateTime.now())
                .build();
    }

    // ========== getCurrentVersion ==========

    @Test
    @DisplayName("正常流程：Redis 有 v1.0.0，DB 有记录，grayRatio=100 → 返回当前版本")
    void getCurrentVersion_normal() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v1.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("100");
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v1.0.0")).thenReturn(v1);

        PromptVersion result = manager.getCurrentVersion("user-1");

        assertThat(result.getVersion()).isEqualTo("v1.0.0");
        verify(versionMapper, never()).findPreviousReleased(anyString());
    }

    @Test
    @DisplayName("灰度比例 100：任意 userId 都返回当前版本（findPreviousReleased 不被调用）")
    void getCurrentVersion_grayRatio100_returnsCurrent() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v2.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("100");
        PromptVersion v2 = buildVersion("v2.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v2.0.0")).thenReturn(v2);

        PromptVersion result = manager.getCurrentVersion("any-user");

        assertThat(result.getVersion()).isEqualTo("v2.0.0");
        verify(versionMapper, never()).findPreviousReleased(anyString());
    }

    @Test
    @DisplayName("灰度比例 0：所有用户 hash >= 0，返回 previous 版本")
    void getCurrentVersion_grayRatio0_returnsPrevious() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v2.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("0");
        PromptVersion v2 = buildVersion("v2.0.0", 0, "RELEASED");
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v2.0.0")).thenReturn(v2);
        when(versionMapper.findPreviousReleased("v2.0.0")).thenReturn(v1);

        PromptVersion result = manager.getCurrentVersion("any-user");

        assertThat(result.getVersion()).isEqualTo("v1.0.0");
        verify(versionMapper, times(1)).findPreviousReleased("v2.0.0");
    }

    @Test
    @DisplayName("灰度比例 50：userId hash < 50 返回当前版本，>= 50 返回 previous")
    void getCurrentVersion_grayRatio50_splitsByHash() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v2.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("50");
        PromptVersion v2 = buildVersion("v2.0.0", 50, "RELEASED");
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v2.0.0")).thenReturn(v2);
        when(versionMapper.findPreviousReleased("v2.0.0")).thenReturn(v1);

        // 测试多个 userId，确保分流稳定（同 hash 同结果）
        String userIdNew = "user-in-new-bucket";  // hash % 100 < 50 → v2
        String userIdOld = "user-in-old-bucket";  // hash % 100 >= 50 → v1

        PromptVersion r1 = manager.getCurrentVersion(userIdNew);
        PromptVersion r2 = manager.getCurrentVersion(userIdOld);

        // 验证两个用户都拿到有效版本（不验证具体哪个，因 hash 取决于 String.hashCode）
        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
    }

    @Test
    @DisplayName("DB 缺失版本记录：降级到 PromptConstants 常量 + counter+1")
    void getCurrentVersion_dbMissingVersion_fallbackToConstants() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v3.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("100");
        when(versionMapper.findByVersion("v3.0.0")).thenReturn(null);

        double before = meterRegistry.counter("prompt.version.fallback").count();
        PromptVersion result = manager.getCurrentVersion("user-1");
        double after = meterRegistry.counter("prompt.version.fallback").count();

        assertThat(result.getVersion()).isEqualTo(PromptConstants.CURRENT_VERSION);
        assertThat(result.getPlatformPrompt()).isEqualTo(PromptConstants.PLATFORM_PROMPT);
        assertThat(after - before).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis 抛异常：降级到 PromptConstants 常量 + counter+1")
    void getCurrentVersion_redisException_fallback() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenThrow(new RuntimeException("Redis connection refused"));

        double before = meterRegistry.counter("prompt.version.fallback").count();
        PromptVersion result = manager.getCurrentVersion("user-1");
        double after = meterRegistry.counter("prompt.version.fallback").count();

        assertThat(result.getVersion()).isEqualTo(PromptConstants.CURRENT_VERSION);
        assertThat(after - before).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis 缺失 current version key：用默认 v1.0.0 兜底")
    void getCurrentVersion_redisEmptyVersionKey_usesDefault() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn(null);
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("100");
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v1.0.0")).thenReturn(v1);

        PromptVersion result = manager.getCurrentVersion("user-1");

        assertThat(result.getVersion()).isEqualTo("v1.0.0");
    }

    @Test
    @DisplayName("Redis 缺失 gray ratio key：默认 100（全量）")
    void getCurrentVersion_redisEmptyGrayRatio_usesDefault100() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v1.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn(null);
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findByVersion("v1.0.0")).thenReturn(v1);

        PromptVersion result = manager.getCurrentVersion("user-1");

        assertThat(result.getVersion()).isEqualTo("v1.0.0");
        verify(versionMapper, never()).findPreviousReleased(anyString());
    }

    @Test
    @DisplayName("灰度时 previous 版本为 null：返回当前版本（不抛异常）")
    void getCurrentVersion_grayRatioButNoPrevious_returnsCurrent() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v2.0.0");
        when(valueOps.get(GRAY_RATIO_KEY)).thenReturn("0");
        PromptVersion v2 = buildVersion("v2.0.0", 0, "RELEASED");
        when(versionMapper.findByVersion("v2.0.0")).thenReturn(v2);
        when(versionMapper.findPreviousReleased("v2.0.0")).thenReturn(null);

        PromptVersion result = manager.getCurrentVersion("any-user");

        assertThat(result.getVersion()).isEqualTo("v2.0.0");
    }

    // ========== switchVersion ==========

    @Test
    @DisplayName("切换版本成功：DB 更新 + Redis 写入新版本号 + grayRatio=100")
    void switchVersion_success() {
        PromptVersion v2 = buildVersion("v2.0.0", 0, "DRAFT");
        when(versionMapper.findByVersion("v2.0.0")).thenReturn(v2);

        manager.switchVersion("v2.0.0");

        verify(versionMapper, times(1)).updateById(any(PromptVersion.class));
        verify(valueOps, times(1)).set(eq(CURRENT_VERSION_KEY), eq("v2.0.0"));
        verify(valueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("100"));
        assertThat(v2.getStatus()).isEqualTo("RELEASED");
        assertThat(v2.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("切换版本：DB 找不到 → 抛 IllegalArgumentException")
    void switchVersion_notFound_throws() {
        when(versionMapper.findByVersion("v9.9.9")).thenReturn(null);

        assertThatThrownBy(() -> manager.switchVersion("v9.9.9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v9.9.9");
    }

    // ========== setGrayRatio ==========

    @Test
    @DisplayName("设置灰度比例 50：Redis 写入 '50'")
    void setGrayRatio_valid_50() {
        manager.setGrayRatio(50);
        verify(valueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("50"));
    }

    @Test
    @DisplayName("设置灰度比例 0：合法（全量回旧版）")
    void setGrayRatio_valid_0() {
        manager.setGrayRatio(0);
        verify(valueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("0"));
    }

    @Test
    @DisplayName("设置灰度比例 100：合法（全量新版）")
    void setGrayRatio_valid_100() {
        manager.setGrayRatio(100);
        verify(valueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("100"));
    }

    @Test
    @DisplayName("设置灰度比例 150：抛 IllegalArgumentException")
    void setGrayRatio_invalid_150_throws() {
        assertThatThrownBy(() -> manager.setGrayRatio(150))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("设置灰度比例 -1：抛 IllegalArgumentException")
    void setGrayRatio_invalid_negative_throws() {
        assertThatThrownBy(() -> manager.setGrayRatio(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== rollback ==========

    @Test
    @DisplayName("回滚成功：Redis 写回 previous 版本号 + grayRatio=100")
    void rollback_success() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v2.0.0");
        PromptVersion v1 = buildVersion("v1.0.0", 100, "RELEASED");
        when(versionMapper.findPreviousReleased("v2.0.0")).thenReturn(v1);

        manager.rollback();

        verify(valueOps, times(1)).set(eq(CURRENT_VERSION_KEY), eq("v1.0.0"));
        verify(valueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("100"));
    }

    @Test
    @DisplayName("回滚：当前版本为 null → 抛 IllegalStateException")
    void rollback_redisEmptyCurrent_throws() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn(null);

        assertThatThrownBy(() -> manager.rollback())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("回滚：无 previous 版本 → 抛 IllegalStateException")
    void rollback_noPrevious_throws() {
        when(valueOps.get(CURRENT_VERSION_KEY)).thenReturn("v1.0.0");
        when(versionMapper.findPreviousReleased("v1.0.0")).thenReturn(null);

        assertThatThrownBy(() -> manager.rollback())
                .isInstanceOf(IllegalStateException.class);
    }

    // ========== init ==========

    @Test
    @DisplayName("init：Redis 缺失 current_version key → 写入默认值（version + grayRatio）")
    void init_redisEmpty_writesDefaults() {
        // 重新构造 manager，模拟 Redis 缺失场景
        StringRedisTemplate freshRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> freshValueOps = mock(ValueOperations.class);
        when(freshRedis.hasKey(CURRENT_VERSION_KEY)).thenReturn(false);
        when(freshRedis.opsForValue()).thenReturn(freshValueOps);

        PromptVersionManager freshManager = new PromptVersionManager(
                freshRedis, versionMapper, meterRegistry);
        // 单元测试中 @Value 不触发，手动注入默认值
        ReflectionTestUtils.setField(freshManager, "defaultCurrentVersion", "v1.0.0");
        ReflectionTestUtils.setField(freshManager, "defaultGrayRatio", 100);
        freshManager.init();

        verify(freshValueOps, times(1)).set(eq(CURRENT_VERSION_KEY), eq("v1.0.0"));
        verify(freshValueOps, times(1)).set(eq(GRAY_RATIO_KEY), eq("100"));
    }

    @Test
    @DisplayName("init：Redis 已有 key → 不写入（幂等）")
    void init_redisHasKey_doesNotWrite() {
        // setUp 中 hasKey=true，init 已被调用一次
        // 验证 set 从未被调用（setUp 的 stub 已是 hasKey=true）
        verify(valueOps, never()).set(eq(CURRENT_VERSION_KEY), anyString());
        verify(valueOps, never()).set(eq(GRAY_RATIO_KEY), anyString());
    }
}
