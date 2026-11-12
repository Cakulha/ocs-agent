package com.ocs.agent.service;

import com.ocs.agent.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmConfig config;
    private final RestClient restClient;

    public LlmClient(LlmConfig config) {
        this.config = config;

        long timeoutMillis = Duration.ofSeconds(config.getTimeoutSeconds()).toMillis();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(timeoutMillis));
        requestFactory.setReadTimeout(Math.toIntExact(timeoutMillis));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Call LLM with the given system prompt and user message (text only).
     *
     * @return the LLM response content string
     */
    public String call(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage, null);
    }

    /**
     * Call LLM with the given system prompt, user message and optional images.
     * When images are present, the user message is sent in OpenAI multimodal
     * format (content array of text + image_url parts).
     *
     * @param images base64 data URLs, ordered to match [图片N] placeholders in the question
     * @return the LLM response content string
     */
    public String call(String systemPrompt, String userMessage, List<String> images) {
        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userMessage, images);

        log.debug("Calling LLM endpoint: {}", config.getEndpoint());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(config.getEndpoint())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("LLM returned empty response");
        }

        return extractContent(response);
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userMessage, List<String> images) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());

        Map<String, Object> systemMessage = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> userMessagePart;
        if (images != null && !images.isEmpty()) {
            // OpenAI multimodal format: content is an array of text/image_url parts
            List<Map<String, Object>> contentParts = new java.util.ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", userMessage));
            for (String image : images) {
                if (image != null && !image.isBlank()) {
                    contentParts.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", image)
                    ));
                }
            }
            userMessagePart = Map.of("role", "user", "content", contentParts);
        } else {
            userMessagePart = Map.of("role", "user", "content", userMessage);
        }
        body.put("messages", List.of(systemMessage, userMessagePart));

        body.put("stream", false);
        body.put("max_tokens", config.getMaxTokens());

        // 始终显式传递 enable_thinking：DSV4.1 等模型默认开启思考模式，
        // 若不显式关闭，推理会耗尽 max_tokens 导致 content 为空
        body.put("chat_template_kwargs", Map.of("enable_thinking", config.isThinking()));

        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM response has no choices");
        }
        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new RuntimeException("LLM response has no message in choice");
        }
        return (String) message.get("content");
    }
}
