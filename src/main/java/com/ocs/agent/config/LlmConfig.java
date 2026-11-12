package com.ocs.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    /** LLM API endpoint URL (OpenAI-compatible) */
    private String endpoint;

    /** API Key for Bearer authentication */
    private String apiKey;

    /** Model name */
    private String model;

    /** HTTP timeout in seconds */
    private int timeoutSeconds = 60;

    /** Enable thinking mode for complex reasoning */
    private boolean thinking = false;
}
