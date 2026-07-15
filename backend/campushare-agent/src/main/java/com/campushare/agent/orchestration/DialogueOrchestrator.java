package com.campushare.agent.orchestration;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.dto.UserProfile;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 对话编排器（LLM-first）。
 *
 * 不再使用模式选择/规则层，而是直接让 LLM 决定是否需要调用工具，
 * 并根据工具结果生成最终回复。
 */
public interface DialogueOrchestrator {

    /**
     * 编排一轮对话。
     *
     * @param userId          用户ID
     * @param sessionId       会话ID
     * @param userMessage     用户原始消息
     * @param intentResult    意图结果（用于检索策略，可被工具覆盖）
     * @param retrievalResults 已检索到的资料
     * @param userProfile     用户画像（昵称等）
     * @param previousRefs    上一轮的引用列表
     * @return 本轮回复
     */
    Mono<TurnResponse> orchestrate(String userId, String sessionId, String userMessage,
                                    IntentResult intentResult, List<RetrievalResult> retrievalResults,
                                    UserProfile userProfile, List<Map<String, Object>> previousRefs);

    /**
     * 总结当前会话。
     */
    Mono<TurnResponse> summarize(String userId, String sessionId);
}
