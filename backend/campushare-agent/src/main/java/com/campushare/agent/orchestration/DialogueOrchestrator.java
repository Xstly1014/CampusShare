package com.campushare.agent.orchestration;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.TurnResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface DialogueOrchestrator {

    Mono<TurnResponse> orchestrate(String userId, String sessionId, String userMessage,
                                    IntentResult intentResult, List<RetrievalResult> retrievalResults);

    Mono<TurnResponse> clarify(String userId, String sessionId, String userMessage,
                               IntentResult intentResult, List<RetrievalResult> retrievalResults);

    Mono<TurnResponse> summarize(String userId, String sessionId);

    Mono<TurnResponse> planAndExecute(String userId, String sessionId, String userMessage,
                                       IntentResult intentResult, List<RetrievalResult> retrievalResults);

    Mono<TurnResponse> reflexion(String userId, String sessionId, String userMessage,
                                  IntentResult intentResult, List<RetrievalResult> retrievalResults);

    OrchestrationMode selectMode(IntentResult intentResult, int turnNumber);
}
