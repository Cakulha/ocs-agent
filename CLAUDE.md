# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**ocs-agent** is a Spring Boot backend AI question-answering service for the OCS (Online Course Script) ecosystem. It integrates with Chinese online course platforms (学习通/超星) via a userscript frontend, accepting questions and returning answers through the LLM.

**Tech stack**: Java 21, Spring Boot 3.4, Maven 3.9

## Commands

```bash
# Compile
mvn clean compile

# Run tests (9 unit tests for AnswerParser)
mvn test

# Run a single test class
mvn test -Dtest=AnswerParserTest

# Package as JAR
mvn clean package

# Run service locally (requires API_KEY)
API_KEY=your-key mvn spring-boot:run
```

## Project Structure

```
ocs-agent/
  pom.xml                                                # Maven build (Spring Boot 3.4.5)
  CLAUDE.md
  .docs/
    AI-TIKU-API.md                                       # API specification
    ai-tiku-config.example.json                          # OCS script configuration example
    DESIGN.md                                            # Design document (Chinese)
  src/
    main/
      java/com/ocs/agent/
        OcsAgentApplication.java                         # Spring Boot entry point
        controller/
          AnswerController.java                          # POST /api/answer
        model/
          AnswerRequest.java                             # Request DTO (question, options, type)
          AnswerResponse.java                            # Response DTO (code, message, data)
          AnswerData.java                                # Nested data.question + data.answer
          QuestionType.java                              # Enum: single/multiple/judgement/completion
        service/
          AnswerService.java                             # Orchestration: prompt → LLM → parse
          LlmClient.java                                 # HTTP client for LLM
          AnswerParser.java                              # Parse LLM output by question type
        config/
          LlmConfig.java                                 # @ConfigurationProperties for LLM settings
      resources/
        application.yml                                  # Server port, LLM endpoint/apiKey/model/timeout
    test/java/com/ocs/agent/service/
      AnswerParserTest.java                              # 9 unit tests covering all question types
```

## API Contract

**`POST /api/answer`** — Accepts `application/json` and `text/plain` Content-Type.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `question` | `string` | Yes | Question text |
| `options` | `string[]` | Yes | Options like `"A. 为人民服务"`, empty for `completion` |
| `type` | `string` | No | `single` / `multiple` / `judgement` / `completion` |

- **Success**: `{ code: 0, message: "success", data: { question, answer } }`
- **No answer**: `{ code: 1, message: "未找到答案" }`

## LLM Integration

- **Model**: Agnes 2.0 Flash (`agnes-2.0-flash`)
- **Endpoint**: `https://apihub.agnes-ai.com/v1/chat/completions`
- **Auth**: Bearer Token (configured via `llm.api-key`)
- **Thinking**: Enabled via `chat_template_kwargs.enable_thinking: true`
- **Timeout**: 60s (configurable)

Configure via `application.yml` or environment variable `API_KEY`.

## Architecture Flow

```
OCS 前端 → POST /api/answer → AnswerController → AnswerService
                                         ↓
                              LlmClient → LLM API
                                         ↓
                              AnswerParser (parse by type)
                                         ↓
                              ← formatted AnswerResponse ←
```

## Key Design Decisions

1. **Content-Type compatibility**: Uses `HttpEntity<String>` to accept both `application/json` and `text/plain` (frontend may auto-add `text/plain;charset=UTF-8`)
2. **Non-streaming LLM calls**: `stream: false` for simplicity
3. **System prompt**: Chinese-language prompt guiding the LLM to output type-specific answer formats
4. **AnswerParser fallback**: Multiple matching strategies (letter extraction, content similarity, keyword matching)
