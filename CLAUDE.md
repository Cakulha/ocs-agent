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

# Run service locally (config via .env file or environment variables)
mvn spring-boot:run
```

## Project Structure

```
ocs-agent/
  pom.xml                                                # Maven build (Spring Boot 3.4.5)
  CLAUDE.md
  .env.example                                           # Environment variable config template
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
          AnswerRequest.java                             # Request DTO (question, options, type, images)
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
        application.yml                                  # Server port, LLM endpoint/apiKey/model/timeout (env-var placeholders)
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
| `images` | `string[]` | No | base64 data URLs for image questions, ordered to match `[图片N]` placeholders in `question` |

- **Success**: `{ code: 0, message: "success", data: { question, answer } }`
- **No answer**: `{ code: 1, message: "未找到答案" }`

## LLM Integration

- **Protocol**: OpenAI-compatible chat completions API
- **Auth**: Bearer Token (configured via `llm.api-key`)
- **Thinking**: Optional via `chat_template_kwargs.enable_thinking: true` (off by default)
- **Timeout**: 60s (configurable)

Endpoint and model name have no built-in defaults — they must be provided via the `.env` file (auto-loaded, see `.env.example`), environment variables (`LLM_API_KEY`, `LLM_ENDPOINT`, `LLM_MODEL`, `LLM_TIMEOUT_SECONDS`, `LLM_THINKING`, `SERVER_PORT`), or `application.yml`. Note: when running a packaged JAR from another directory, the `.env` path resolves relative to the working directory.

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
5. **Multimodal image support**: When the request carries `images` (base64 data URLs injected by OCS "图片题优化"), the user message is sent in OpenAI multimodal format (content array of `text` + `image_url` parts); `[图片N]` placeholders in question/options map to the Nth image and are passed through in answers
6. **GET status endpoint**: `GET /api/answer` returns 200 for OCS connectivity probes (HEAD is auto-routed by Spring MVC); only POST triggers LLM calls
