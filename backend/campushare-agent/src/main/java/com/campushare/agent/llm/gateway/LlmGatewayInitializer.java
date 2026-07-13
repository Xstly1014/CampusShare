package com.campushare.agent.llm.gateway;

import com.campushare.agent.llm.gateway.adapter.DeepSeekAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGatewayInitializer {

    private final LlmGateway llmGateway;
    private final DeepSeekAdapter deepSeekAdapter;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        llmGateway.registerProvider(deepSeekAdapter);
        log.info("LLM Gateway initialized with {} providers",
                llmGateway.getProviderStatuses().size());
    }
}
