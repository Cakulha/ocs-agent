package com.ocs.agent.service;

import com.ocs.agent.model.AnswerRequest;
import com.ocs.agent.model.AnswerResponse;
import com.ocs.agent.model.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final LlmClient llmClient;
    private final AnswerParser answerParser;

    private static final String SYSTEM_PROMPT = """
            你是一个在线课程答题助手。请根据题目和选项，给出正确答案。
            根据题型，严格按照以下格式回答，不要输出任何解释或其他内容：
            
            - 单选题：只输出一个选项字母，如 "A"
            - 多选题：用 # 连接多个选项字母，如 "A#C#D"
            - 判断题：输出 "正确" 或 "错误"
            - 填空题：直接输出答案文本
            """;

    public AnswerService(LlmClient llmClient, AnswerParser answerParser) {
        this.llmClient = llmClient;
        this.answerParser = answerParser;
    }

    /**
     * Process an answer request: build prompt → call LLM → parse response.
     */
    public AnswerResponse answer(AnswerRequest request) {
        request.normalize();
        try {
            String userMessage = buildUserMessage(request);
            log.debug("userMessage=\n{}", userMessage);

            long beforeLLMCall = System.currentTimeMillis();
            String llmResponse = llmClient.call(SYSTEM_PROMPT, userMessage);
            String parsedAnswer = answerParser.parse(llmResponse, request.getType(), request.getOptions());
            long afterLLMCall = System.currentTimeMillis();

            log.debug("LLM raw answer: {}， Parsed answer: {}", llmResponse, parsedAnswer);
            log.info("\n{}\n答案: {}\n耗时: {}s", userMessage, parsedAnswer, TimeUnit.MILLISECONDS.toSeconds(afterLLMCall - beforeLLMCall));
            return AnswerResponse.success(request.getQuestion(), parsedAnswer);

        } catch (Exception e) {
            log.error("Error processing question: {}", e.getMessage(), e);
            return AnswerResponse.notFound();
        }
    }

    private String buildUserMessage(AnswerRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("题目：").append(request.getQuestion()).append("\n");

        if ((request.getType() == QuestionType.single || request.getType() == QuestionType.multiple) && request.getOptions() != null && !request.getOptions().isEmpty()) {
            sb.append("选项：\n");
            for (String option : request.getOptions()) {
                sb.append(option).append("\n");
            }
        }

        sb.append("题型：").append(typeDescription(request.getType()));
        return sb.toString();
    }

    private String typeDescription(QuestionType type) {
        return switch (type) {
            case single -> "单选题";
            case multiple -> "多选题";
            case judgement -> "判断题";
            case completion -> "填空题";
        };
    }
}
