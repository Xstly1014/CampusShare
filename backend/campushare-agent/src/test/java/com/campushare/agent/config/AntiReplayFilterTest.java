package com.campushare.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@SpringBootTest
@DisplayName("AntiReplayFilter 单元测试")
class AntiReplayFilterTest {

    @Autowired
    private AntiReplayFilter antiReplayFilter;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("基本测试")
    class BasicTests {

        @Test
        @DisplayName("过滤器成功加载")
        void filter_loadsSuccessfully() {
            org.junit.jupiter.api.Assertions.assertNotNull(antiReplayFilter);
        }
    }
}
