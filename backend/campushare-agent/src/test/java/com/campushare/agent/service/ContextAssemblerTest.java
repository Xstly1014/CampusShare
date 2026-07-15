package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.ChatContext;
import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.ContextSnapshot;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.UserProfile;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.mapper.AgentTurnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ContextAssembler 单元测试（ADR-070~076）。
 *
 * 验证点：
 *  - L0-L5 分层组装：system → history(user/assistant 交替) → user
 *  - L1 用户画像追加到 system message 末尾
 *  - 按意图分配 Token 预算（HOW_TO/SEARCH/NAVIGATE/CLARIFY）
 *  - L4 历史超预算时截断（保留最近的轮次）
 *  - 3 级降级链：L4→2轮 → L1丢弃 → L4→1轮
 *  - 快照字段：truncated / truncationReason / layerTokens
 *  - 空历史处理
 *  - null 用户画像处理
 *  - 长期记忆用户画像提取（nickname）
 *  - 上一轮 refs 加载与解析
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContextAssembler 单元测试")
class ContextAssemblerTest {

    @InjectMocks
    private ContextAssembler assembler;

    @Mock
    private MemoryRetrievalService memoryRetrievalService;

    @Mock
    private AgentTurnMapper agentTurnMapper;

    @BeforeEach
    void setUp() {
        // @InjectMocks 已负责实例化；本方法保留用于后续扩展
    }

    private IntentResult intent(Intent type) {
        return IntentResult.builder()
                .intent(type)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.9)
                .rewrittenQuery("test query")
                .classifyLayer("RULE")
                .build();
    }

    private AgentTurn turn(int number, String user, String assistant) {
        return AgentTurn.builder()
                .sessionId("session-1")
                .turnNumber(number)
                .userMessage(user)
                .assistantMessage(assistant)
                .status("COMPLETED")
                .build();
    }

    private RetrievalResult profileMemory(String id, String key, String value, String updatedAt) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("memoryKey", key);
        meta.put("updatedAt", updatedAt);
        return RetrievalResult.memory(id, key, value, 0.9, meta);
    }

    @Nested
    @DisplayName("基础组装")
    class BasicAssembly {

        @Test
        @DisplayName("空历史 + null 画像：system + user 两条消息")
        void assemble_emptyHistory_nullProfile() {
            IntentResult ir = intent(Intent.HOW_TO);
            String systemPrompt = "You are a helpful assistant.";
            String userQuery = "How do I post?";

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, userQuery, ir, systemPrompt, null, null, null, null);

            assertThat(result.messages()).hasSize(2);
            assertThat(result.messages().get(0).getRole()).isEqualTo("system");
            assertThat(result.messages().get(0).getContent()).contains(systemPrompt);
            assertThat(result.messages().get(1).getRole()).isEqualTo("user");
            assertThat(result.messages().get(1).getContent()).contains(userQuery);
        }

        @Test
        @DisplayName("有历史 + null 画像：system → history(user/assistant交替) → user")
        void assemble_withHistory_correctOrder() {
            IntentResult ir = intent(Intent.HOW_TO);
            List<AgentTurn> history = List.of(
                    turn(1, "question 1", "answer 1"),
                    turn(2, "question 2", "answer 2")
            );

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 3, "question 3", ir, "system prompt", history, null, null, null);

            // system(1) + 2*(user+assistant)(4) + current user(1) = 6
            assertThat(result.messages()).hasSize(6);
            assertThat(result.messages().get(0).getRole()).isEqualTo("system");
            assertThat(result.messages().get(1).getRole()).isEqualTo("user");
            assertThat(result.messages().get(1).getContent()).isEqualTo("question 1");
            assertThat(result.messages().get(2).getRole()).isEqualTo("assistant");
            assertThat(result.messages().get(2).getContent()).isEqualTo("answer 1");
            assertThat(result.messages().get(3).getRole()).isEqualTo("user");
            assertThat(result.messages().get(3).getContent()).isEqualTo("question 2");
            assertThat(result.messages().get(4).getRole()).isEqualTo("assistant");
            assertThat(result.messages().get(4).getContent()).isEqualTo("answer 2");
            assertThat(result.messages().get(5).getRole()).isEqualTo("user");
            assertThat(result.messages().get(5).getContent()).contains("question 3");
        }

        @Test
        @DisplayName("L1 用户画像：追加到 system message 末尾")
        void assemble_withProfile_appendedToSystem() {
            IntentResult ir = intent(Intent.HOW_TO);
            String profile = "用户偏好：中文回答，喜欢简洁";

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "hello", ir, "base system", null, profile, null, null);

            assertThat(result.messages().get(0).getRole()).isEqualTo("system");
            assertThat(result.messages().get(0).getContent())
                    .contains("base system")
                    .contains("<user_profile>")
                    .contains(profile);
        }

        @Test
        @DisplayName("空 list 历史 + 空字符串画像：仅 system + user")
        void assemble_emptyListHistory_emptyProfile() {
            IntentResult ir = intent(Intent.SEARCH);

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "search query", ir, "system", Collections.emptyList(), "", null, null);

            assertThat(result.messages()).hasSize(2);
            // 空字符串画像不追加到 system，system prompt 用 XML 标签包裹
            assertThat(result.messages().get(0).getContent()).contains("system").doesNotContain("<user_profile>");
        }
    }

    @Nested
    @DisplayName("Token 预算与意图分配")
    class TokenBudget {

        @Test
        @DisplayName("快照 layerTokens 包含 L0/L1/L4/L5/TOTAL")
        void assemble_snapshot_layerTokensComplete() {
            IntentResult ir = intent(Intent.HOW_TO);

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "query", ir, "system prompt", null, null, null, null);

            ContextSnapshot snapshot = result.snapshot();
            assertThat(snapshot.layerTokens()).containsKeys("L0_SYSTEM", "L1_PROFILE", "L2_TOOL_DEFS", "L4_HISTORY", "L5_USER_INPUT", "TOTAL");
            assertThat(snapshot.layerTokens().get("L0_SYSTEM")).isGreaterThan(0);
            assertThat(snapshot.layerTokens().get("L5_USER_INPUT")).isGreaterThan(0);
            assertThat(snapshot.layerTokens().get("L4_HISTORY")).isEqualTo(0);
            assertThat(snapshot.layerTokens().get("L1_PROFILE")).isEqualTo(0);
        }

        @Test
        @DisplayName("totalTokens 等于各层 token 之和")
        void assemble_totalTokens_equalsSumOfLayers() {
            IntentResult ir = intent(Intent.SEARCH);
            List<AgentTurn> history = List.of(turn(1, "q1", "a1"));

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 2, "q2", ir, "system", history, "profile", null, null);

            int l0 = result.snapshot().layerTokens().get("L0_SYSTEM");
            int l1 = result.snapshot().layerTokens().get("L1_PROFILE");
            int l2 = result.snapshot().layerTokens().get("L2_TOOL_DEFS");
            int l4 = result.snapshot().layerTokens().get("L4_HISTORY");
            int l5 = result.snapshot().layerTokens().get("L5_USER_INPUT");

            assertThat(result.totalTokens()).isEqualTo(l0 + l1 + l2 + l4 + l5);
        }

        @Test
        @DisplayName("null intent 时使用 DEFAULT 预算")
        void assemble_nullIntent_usesDefaultBudget() {
            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "query", null, "system", null, null, null, null);

            // 不抛异常，使用 DEFAULT 预算
            assertThat(result.messages()).hasSize(2);
            assertThat(result.totalTokens()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("L4 历史截断")
    class HistoryTruncation {

        @Test
        @DisplayName("L4 超预算时截断到最近轮次，truncated=true")
        void assemble_historyExceedsBudget_truncated() {
            IntentResult ir = intent(Intent.HOW_TO);
            // HOW_TO 的 L4 预算 = 1000 tokens
            // 构造超长历史，每轮约 500 tokens
            String longMsg = "word ".repeat(200); // ~200 tokens per message
            List<AgentTurn> history = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                history.add(turn(i, longMsg, longMsg));
            }

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 11, "query", ir, "system", history, null, null, null);

            assertThat(result.snapshot().truncated()).isTrue();
            assertThat(result.snapshot().truncationReason()).isEqualTo("L4_HISTORY_TRUNCATED");
            // 截断后历史轮数应少于 10
            // messages = system + (user+assistant)*keptRounds + current_user
            // (messages.size - 2) / 2 = keptRounds
            int keptRounds = (result.messages().size() - 2) / 2;
            assertThat(keptRounds).isLessThan(10);
        }

        @Test
        @DisplayName("L4 未超预算时不截断，truncated=false")
        void assemble_historyWithinBudget_notTruncated() {
            IntentResult ir = intent(Intent.HOW_TO);
            List<AgentTurn> history = List.of(
                    turn(1, "short q1", "short a1"),
                    turn(2, "short q2", "short a2")
            );

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 3, "query", ir, "system", history, null, null, null);

            assertThat(result.snapshot().truncated()).isFalse();
            assertThat(result.snapshot().truncationReason()).isNull();
            assertThat(result.messages()).hasSize(6); // system + 4 history + 1 user
        }
    }

    @Nested
    @DisplayName("3 级降级链")
    class DegradationChain {

        @Test
        @DisplayName("降级 1：total 超预算时 L4 截断到 2 轮")
        void assemble_totalExceeds_degradeTo2Rounds() {
            IntentResult ir = intent(Intent.HOW_TO);
            // 构造超大 system prompt 使 total > 8000
            // HOW_TO: total budget = 8000
            // system prompt ~10000 tokens → l0 ~10004, 远超 8000
            String hugeSystem = "word ".repeat(10000);
            List<AgentTurn> history = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                history.add(turn(i, "q" + i, "a" + i));
            }

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 6, "query", ir, hugeSystem, history, null, null, null);

            assertThat(result.snapshot().truncated()).isTrue();
            // 降级原因应该是 DEGRADE_L4_TO_2_ROUNDS 或更严重
            assertThat(result.snapshot().truncationReason())
                    .satisfiesAnyOf(
                            reason -> assertThat(reason).isEqualTo("DEGRADE_L4_TO_2_ROUNDS"),
                            reason -> assertThat(reason).isEqualTo("HARD_LIMIT_L4_TO_1_ROUND")
                    );
            // 降级后最多保留 2 轮历史
            int keptRounds = (result.messages().size() - 2) / 2;
            assertThat(keptRounds).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("降级 2：L1 用户画像被丢弃")
        void assemble_totalExceeds_degradeL1Dropped() {
            IntentResult ir = intent(Intent.HOW_TO);
            // 构造超大 system + 有用户画像，确保 degrade 1 后仍超预算
            String hugeSystem = "word ".repeat(10000);
            String profile = "user profile content for testing degradation";
            List<AgentTurn> history = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                history.add(turn(i, "question " + i, "answer " + i));
            }

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 6, "query", ir, hugeSystem, history, profile, null, null);

            assertThat(result.snapshot().truncated()).isTrue();
            // system prompt 过大时 L1 必然被丢弃（degrade 2）
            // system message 不应包含用户画像
            assertThat(result.messages().get(0).getContent())
                    .doesNotContain("<user_profile>");
        }

        @Test
        @DisplayName("降级 3：硬上限 L4 截断到 1 轮")
        void assemble_totalExceeds_hardLimit() {
            IntentResult ir = intent(Intent.HOW_TO);
            // 构造超大 system prompt，确保触发硬上限
            String hugeSystem = "word ".repeat(10000);
            List<AgentTurn> history = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                history.add(turn(i, "q" + i, "a" + i));
            }

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 11, "query", ir, hugeSystem, history, null, null, null);

            assertThat(result.snapshot().truncated()).isTrue();
            // 硬上限后最多 1 轮历史
            int keptRounds = (result.messages().size() - 2) / 2;
            assertThat(keptRounds).isLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("快照字段")
    class SnapshotFields {

        @Test
        @DisplayName("快照包含 sessionId 和 turnId")
        void assemble_snapshot_containsSessionAndTurn() {
            IntentResult ir = intent(Intent.SEARCH);

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-abc", 42, "query", ir, "system", null, null, null, null);

            ContextSnapshot snapshot = result.snapshot();
            assertThat(snapshot.sessionId()).isEqualTo("session-abc");
            assertThat(snapshot.turnId()).isEqualTo(42);
        }

        @Test
        @DisplayName("快照 messages 与返回 messages 一致")
        void assemble_snapshot_messagesMatchReturned() {
            IntentResult ir = intent(Intent.SEARCH);
            List<AgentTurn> history = List.of(turn(1, "q1", "a1"));

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 2, "q2", ir, "system", history, null, null, null);

            assertThat(result.snapshot().messages()).isEqualTo(result.messages());
            assertThat(result.snapshot().messages()).hasSameSizeAs(result.messages());
        }

        @Test
        @DisplayName("快照 totalInputTokens 与 totalTokens 一致")
        void assemble_snapshot_totalInputTokensMatches() {
            IntentResult ir = intent(Intent.SEARCH);

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "query", ir, "system", null, null, null, null);

            assertThat(result.snapshot().totalInputTokens()).isEqualTo(result.totalTokens());
        }

        @Test
        @DisplayName("快照 usedMemoryIds 为 null（P2 实现）")
        void assemble_snapshot_usedMemoryIdsNull() {
            IntentResult ir = intent(Intent.SEARCH);

            ContextAssembler.AssembledContext result = assembler.assemble(
                    "session-1", 1, "query", ir, "system", null, null, null, null);

            assertThat(result.snapshot().usedMemoryIds()).isNull();
        }
    }

    @Nested
    @DisplayName("记忆与引用加载")
    class MemoryAndRefsAssembly {

        @Test
        @DisplayName("从 profile memories 中提取最新 nickname")
        void assembleWithMemory_extractsLatestNickname() {
            List<RetrievalResult> memories = List.of(
                    profileMemory("1", "nickname", "old", "2024-01-01T00:00:00Z"),
                    profileMemory("2", "nickname", "yuuki", "2024-06-01T00:00:00Z"),
                    profileMemory("3", "major", "CS", "2025-01-01T00:00:00Z")
            );
            when(memoryRetrievalService.loadProfileMemories("user-1")).thenReturn(memories);
            when(agentTurnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AgentSession session = AgentSession.builder().id("session-1").build();
            AgentTurn turn = AgentTurn.builder().sessionId("session-1").turnNumber(2).build();
            ChatRequest request = new ChatRequest();
            request.setMessage("hello");

            ChatContext ctx = assembler.assembleWithMemory("user-1", request, session, turn).block();

            assertThat(ctx).isNotNull();
            assertThat(ctx.userProfile()).isNotNull();
            assertThat(ctx.userProfile().nickname()).isEqualTo("yuuki");
            assertThat(ctx.previousRefs()).isEmpty();
        }

        @Test
        @DisplayName("无 nickname 时返回空昵称")
        void assembleWithMemory_noNickname_returnsNullNickname() {
            List<RetrievalResult> memories = List.of(
                    profileMemory("1", "major", "CS", "2024-01-01T00:00:00Z")
            );
            when(memoryRetrievalService.loadProfileMemories("user-1")).thenReturn(memories);
            when(agentTurnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AgentSession session = AgentSession.builder().id("session-1").build();
            AgentTurn turn = AgentTurn.builder().sessionId("session-1").turnNumber(1).build();
            ChatRequest request = new ChatRequest();
            request.setMessage("hello");

            ChatContext ctx = assembler.assembleWithMemory("user-1", request, session, turn).block();

            assertThat(ctx).isNotNull();
            assertThat(ctx.userProfile()).isNotNull();
            assertThat(ctx.userProfile().nickname()).isNull();
        }

        @Test
        @DisplayName("加载并解析上一轮 refs")
        void assembleWithMemory_loadsPreviousRefs() {
            when(memoryRetrievalService.loadProfileMemories("user-1")).thenReturn(Collections.emptyList());

            AgentTurn previousTurn = AgentTurn.builder()
                    .sessionId("session-1")
                    .turnNumber(1)
                    .refs("[{\"index\":1,\"id\":\"post-1\",\"type\":\"POST\",\"title\":\"t1\",\"url\":\"/post/post-1\"}]")
                    .build();
            when(agentTurnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(previousTurn);

            AgentSession session = AgentSession.builder().id("session-1").build();
            AgentTurn turn = AgentTurn.builder().sessionId("session-1").turnNumber(2).build();
            ChatRequest request = new ChatRequest();
            request.setMessage("tell me more");

            ChatContext ctx = assembler.assembleWithMemory("user-1", request, session, turn).block();

            assertThat(ctx).isNotNull();
            assertThat(ctx.previousRefs()).hasSize(1);
            assertThat(ctx.previousRefs().get(0)).containsEntry("index", 1)
                    .containsEntry("id", "post-1")
                    .containsEntry("type", "POST");
        }

        @Test
        @DisplayName("无上一轮 refs 时返回空列表")
        void assembleWithMemory_noPreviousRefs_returnsEmptyList() {
            when(memoryRetrievalService.loadProfileMemories("user-1")).thenReturn(Collections.emptyList());

            AgentTurn previousTurn = AgentTurn.builder()
                    .sessionId("session-1")
                    .turnNumber(1)
                    .build();
            when(agentTurnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(previousTurn);

            AgentSession session = AgentSession.builder().id("session-1").build();
            AgentTurn turn = AgentTurn.builder().sessionId("session-1").turnNumber(2).build();
            ChatRequest request = new ChatRequest();
            request.setMessage("hello");

            ChatContext ctx = assembler.assembleWithMemory("user-1", request, session, turn).block();

            assertThat(ctx).isNotNull();
            assertThat(ctx.previousRefs()).isEmpty();
        }

        @Test
        @DisplayName("buildProfilePrompt 有昵称时返回提示片段")
        void buildProfilePrompt_withNickname_returnsSnippet() {
            String snippet = assembler.buildProfilePrompt(new UserProfile("yuuki"));
            assertThat(snippet).isEqualTo("用户昵称：yuuki\n请使用用户昵称进行友好回应。");
        }

        @Test
        @DisplayName("buildProfilePrompt 无昵称时返回空字符串")
        void buildProfilePrompt_withoutNickname_returnsEmpty() {
            assertThat(assembler.buildProfilePrompt(new UserProfile(null))).isEmpty();
            assertThat(assembler.buildProfilePrompt(new UserProfile(""))).isEmpty();
            assertThat(assembler.buildProfilePrompt(null)).isEmpty();
        }
    }
}
