package com.ocs.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocs.agent.model.AnswerRequest;
import com.ocs.agent.model.AnswerResponse;
import com.ocs.agent.service.AnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the OCS question-answering endpoint.
 *
 * Accepts both application/json and text/plain Content-Type
 * (frontend may auto-add text/plain;charset=UTF-8).
 */
@RestController
public class AnswerController {

    private static final Logger log = LoggerFactory.getLogger(AnswerController.class);

    private final AnswerService answerService;
    private final ObjectMapper objectMapper;

    public AnswerController(AnswerService answerService, ObjectMapper objectMapper) {
        this.answerService = answerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/answer")
    public AnswerResponse answer(HttpEntity<String> httpEntity) {
        String body = httpEntity.getBody();
        log.debug("Received request body: {}", body);

        if (body == null || body.isBlank()) {
            return AnswerResponse.error("请求体为空");
        }

        try {
            AnswerRequest request = objectMapper.readValue(body, AnswerRequest.class);
            return answerService.answer(request);
        } catch (Exception e) {
            log.warn("Failed to parse request body: {}", e.getMessage());
            return AnswerResponse.error("请求解析失败: " + e.getMessage());
        }
    }
}
