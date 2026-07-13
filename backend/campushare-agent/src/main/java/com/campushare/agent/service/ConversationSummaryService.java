package com.campushare.agent.service;

import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final DeepSeekClient deepSeekClient;

    private static final String SUMMARY_PROMPT = """
            请对以下对话进行总结：

            对话历史：
            %s

            请输出简洁的总结，包括：
            1. 用户的主要问题或需求
            2. 提供的解决方案或回答要点
            3. 关键步骤或建议

            输出格式：
            ## 总结
            [总结内容]
            """.trim();

    public String summarizeSession(AgentSession session, List<AgentTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "暂无对话内容";
        }

        StringBuilder conversationHistory = new StringBuilder();
        for (AgentTurn turn : turns) {
            if (turn.getUserMessage() != null) {
                conversationHistory.append("用户: ").append(turn.getUserMessage()).append("\n");
            }
            if (turn.getAssistantMessage() != null) {
                conversationHistory.append("助手: ").append(turn.getAssistantMessage()).append("\n");
            }
        }

        String prompt = SUMMARY_PROMPT.replace("%s", conversationHistory.toString().trim());

        try {
            List<DeepSeekRequest.Message> messages = List.of(
                    DeepSeekRequest.Message.builder()
                            .role("system")
                            .content("你是一个专业的对话总结助手，擅长提炼对话核心内容。")
                            .build(),
                    DeepSeekRequest.Message.builder()
                            .role("user")
                            .content(prompt)
                            .build()
            );

            DeepSeekResponse response = deepSeekClient.chatCompletion(messages).block();
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                log.debug("Session summary generated: sessionId={}, length={}",
                        session.getId(), content != null ? content.length() : 0);
                return content != null ? content.trim() : "总结生成失败";
            }
        } catch (Exception e) {
            log.warn("Failed to generate session summary: sessionId={}", session.getId(), e);
        }

        return generateFallbackSummary(turns);
    }

    public String summarizeTurn(List<AgentTurn> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                context.append("Q: ").append(truncate(turn.getUserMessage(), 200)).append("\n");
            }
            if (turn.getAssistantMessage() != null) {
                context.append("A: ").append(truncate(turn.getAssistantMessage(), 300)).append("\n");
            }
        }

        String prompt = """
                请用一句话总结以下对话要点：
                
                %s
                
                输出：[一句话总结]
                """.formatted(context.toString().trim());

        try {
            List<DeepSeekRequest.Message> messages = List.of(
                    DeepSeekRequest.Message.builder()
                            .role("system")
                            .content("你是一个专业的对话摘要助手，输出简洁的一句话总结。")
                            .build(),
                    DeepSeekRequest.Message.builder()
                            .role("user")
                            .content(prompt)
                            .build()
            );

            DeepSeekResponse response = deepSeekClient.chatCompletion(messages).block();
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                return content != null ? content.trim() : "";
            }
        } catch (Exception e) {
            log.debug("Failed to generate turn summary", e);
        }

        return "";
    }

    private String generateFallbackSummary(List<AgentTurn> turns) {
        StringBuilder summary = new StringBuilder("## 总结\n");
        summary.append("本次对话包含 ").append(turns.size()).append(" 轮交互。\n");

        List<String> userQuestions = new ArrayList<>();
        for (AgentTurn turn : turns) {
            if (turn.getUserMessage() != null && turn.getUserMessage().length() > 0) {
                userQuestions.add(truncate(turn.getUserMessage(), 50));
            }
        }

        if (!userQuestions.isEmpty()) {
            summary.append("用户问题：").append(String.join("；", userQuestions));
        }

        return summary.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}