package com.campushare.agent.filter;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.service.LongTermMemoryService;
import com.campushare.agent.service.RuleShortCircuitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RuleShortCircuitFilter 昵称识别单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleShortCircuitFilter 昵称识别测试")
class RuleShortCircuitFilterTest {

    @Mock
    private LongTermMemoryService longTermMemoryService;

    private RuleShortCircuitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RuleShortCircuitFilter(longTermMemoryService);
    }

    @Test
    @DisplayName("我叫 yuuki：识别昵称并保存")
    void filter_nickname_我叫yuuki() {
        String userId = "user-123";
        Optional<IntentResult> result = filter.filter(userId, "我叫yuuki");

        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
        assertThat(result.get().getTemplateReply()).isEqualTo("好的 yuuki，我记住啦～");
        assertThat(result.get().isNicknameDeclared()).isTrue();

        verify(longTermMemoryService).saveNickname(userId, "yuuki");
    }

    @Test
    @DisplayName("叫我 小明：识别昵称并保存")
    void filter_nickname_叫我小明() {
        String userId = "user-456";
        Optional<IntentResult> result = filter.filter(userId, "叫我 小明");

        assertThat(result).isPresent();
        assertThat(result.get().getTemplateReply()).isEqualTo("好的 小明，我记住啦～");
        assertThat(result.get().isNicknameDeclared()).isTrue();

        verify(longTermMemoryService).saveNickname(userId, "小明");
    }

    @Test
    @DisplayName("我的名字是 张三：识别昵称并保存")
    void filter_nickname_我的名字是张三() {
        String userId = "user-789";
        Optional<IntentResult> result = filter.filter(userId, "我的名字是张三");

        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
        assertThat(result.get().getTemplateReply()).isEqualTo("好的 张三，我记住啦～");
        assertThat(result.get().isNicknameDeclared()).isTrue();

        verify(longTermMemoryService).saveNickname(userId, "张三");
    }

    @Test
    @DisplayName("无昵称声明：保持原有闲聊逻辑")
    void filter_noNickname_returnsNormalChitchat() {
        String userId = "user-000";
        Optional<IntentResult> result = filter.filter(userId, "你好");

        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(result.get().getSubIntent()).isEqualTo(Intent.SubIntent.CHITCHAT);
        assertThat(result.get().getTemplateReply()).isNull();
        assertThat(result.get().isNicknameDeclared()).isFalse();

        verify(longTermMemoryService, never()).saveNickname(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
