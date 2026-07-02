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

    private static final long TIMEOUT_MILLIS = Duration.ofSeconds(60).toMillis();

    private final LlmConfig config;
    private final RestClient restClient;

    public LlmClient(LlmConfig config) {
        this.config = config;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(Math.toIntExact(TIMEOUT_MILLIS));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Call LLM with the given system prompt and user message.
     *
     * @return the LLM response content string
     */
    public String call(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = buildRequestBody(systemPrompt, userMessage);

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

    private Map<String, Object> buildRequestBody(String systemPrompt, String userMessage) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", config.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("stream", false);
        body.put("max_tokens", 1024);

        if (config.isThinking()) {
            body.put("chat_template_kwargs", Map.of("enable_thinking", true));
        }

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
