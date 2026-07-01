package com.ocs.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    /** Agnes API endpoint URL */
    private String endpoint = "https://apihub.agnes-ai.com/v1/chat/completions";

    /** API Key for Bearer authentication */
    private String apiKey;

    /** Model name */
    private String model = "agnes-2.0-flash";

    /** HTTP timeout in seconds */
    private int timeoutSeconds = 60;

    /** Enable thinking mode for complex reasoning */
    private boolean thinking = true;
}
