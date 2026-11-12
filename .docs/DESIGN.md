# OCS Agent 设计文档

## 概述

OCS Agent 是一个基于 Spring Boot 的后端服务，为 OCS (Online Course Script) 前端提供 AI 自动答题能力。它接收前端传递的题目参数，调用 OpenAI 兼容协议的大模型推理，解析模型回复后返回格式化答案。

## 系统架构

```
┌─────────────────────┐      POST /api/answer      ┌──────────────────────────┐
│                     │  ──────────────────────────▶│                          │
│   OCS 前端脚本       │      (JSON body)            │    OCS Agent 后端         │
│   (学习通/超星)       │                              │    (Spring Boot 3.4)     │
│                     │◀──────────────────────────────│                          │
└─────────────────────┘      200 OK (JSON)           └──────────┬───────────────┘
                                                                 │
                                                                 │ POST /v1/chat/completions
                                                                 │ (Bearer Token)
                                                                 ▼
                                                     ┌──────────────────────────┐
                                                     │                         │
                                                     │  LLM API                │
                                                     │  (OpenAI 兼容协议)       │
                                                     │                         │
                                                     └──────────────────────────┘
```

## 组件说明

### 1. AnswerController

**路径**: `src/main/java/com/ocs/agent/controller/AnswerController.java`

接收前端 POST 请求，兼容 `application/json` 和 `text/plain` 两种 Content-Type（前端可能自动添加 `text/plain;charset=UTF-8`）。使用 `HttpEntity<String>` 获取原始请求体，然后手动反序列化为 `AnswerRequest`。

### 2. AnswerService

**路径**: `src/main/java/com/ocs/agent/service/AnswerService.java`

业务编排核心。负责：
1. 构建 System Prompt（中英文混合，指导 LLM 按题型输出格式化答案）
2. 构建 User Message（包含题目、选项、题型描述）
3. 调用 LlmClient 与大模型通信（携带图片时走多模态格式）
4. 调用 AnswerParser 解析 LLM 回复
5. 返回 AnswerResponse

**System Prompt 策略**:
```
你是一个在线课程答题助手。请根据题目和选项，给出正确答案。
严格按照以下格式回答，不要输出任何解释或其他内容：

- 单选题：只输出选项字母，如 "A"
- 多选题：用 # 连接多个选项字母，如 "A#C#D"
- 判断题：输出 "正确" 或 "错误"
- 填空题：直接输出答案文本

题目或选项中可能出现 [图片N] 占位符（如 [图片1]、[图片2]），表示此处有一张图片。
图片会以附件形式随消息一并提供，按顺序与占位符编号对应（[图片1] 对应第一张附件图片）。
请结合图片内容理解题目并作答；若答案本身包含图片，请在答案中保留 [图片N] 占位符。
```

### 3. LlmClient

**路径**: `src/main/java/com/ocs/agent/service/LlmClient.java`

HTTP 客户端，调用 OpenAI 兼容协议的大模型 API：
- **Endpoint**: 通过 `LLM_ENDPOINT` 配置（如 `https://your-llm-host/v1/chat/completions`）
- **认证**: Bearer Token
- **模型**: 通过 `LLM_MODEL` 配置
- **模式**: 非流式 (`stream: false`)
- **超时**: 60 秒（可配置）
- **Thinking**: 可选启用 `chat_template_kwargs.enable_thinking: true`（默认关闭）
- **多模态**: 请求携带 `images` 时，user message 的 content 使用 OpenAI 多模态数组格式（`text` + `image_url` parts），图片为 base64 data URL

### 4. AnswerParser

**路径**: `src/main/java/com/ocs/agent/service/AnswerParser.java`

按题型解析 LLM 回复：

| 题型 | 解析策略 | 输出示例 |
|------|----------|----------|
| single | 提取大写字母 → 选项内容匹配 → 兜底原样返回 | `"B"` |
| multiple | 提取所有大写字母，用 `#` 连接 | `"A#C#D"` |
| judgement | 关键词匹配（正向：正确/对/是/true/yes → "正确"；负向：错误/错/否/false/no → "错误"） | `"正确"` |
| completion | 原样返回 | `"为人民服务"` |

### 5. 数据模型

| 类 | 字段 | 说明 |
|----|------|------|
| `AnswerRequest` | question, options, type, images | 前端请求体 |
| `AnswerResponse` | code, message, data | 统一响应格式 |
| `AnswerData` | question, answer | 响应数据体 |
| `QuestionType` | single/multiple/judgement/completion | 题型枚举 |

## 配置说明

### .env 文件（推荐）

在项目根目录创建 `.env` 文件（参考 `.env.example`），服务启动时自动加载：

```properties
LLM_API_KEY=your-api-key
LLM_ENDPOINT=https://your-llm-host/v1/chat/completions
LLM_MODEL=your-model-name
LLM_TIMEOUT_SECONDS=60
LLM_THINKING=false
SERVER_PORT=3000
```

> 注意：`.env` 文件路径相对于运行时工作目录解析。以打包 JAR 方式在其他目录运行时，需确保 `.env` 位于当前工作目录，或改用环境变量。

### application.yml

```yaml
server:
  port: ${SERVER_PORT:3000}                    # 服务端口

llm:
  api-key: ${LLM_API_KEY:${API_KEY:}}          # API Key（优先 LLM_API_KEY，兼容旧 API_KEY）
  endpoint: ${LLM_ENDPOINT:}                   # API 地址（无内置默认值，必须配置）
  model: ${LLM_MODEL:}                         # 模型名称（无内置默认值，必须配置）
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:60}   # HTTP 超时时间
  thinking: ${LLM_THINKING:false}              # 启用 Thinking 模式
```

## API 接口

详见 [AI-TIKU-API.md](AI-TIKU-API.md)。

### 接口地址

```
POST /api/answer
```

### 请求示例

```json
{
  "question": "中国共产党的根本宗旨是（）",
  "options": ["A. 为人民服务", "B. 实现共产主义", "C. 依法治国", "D. 改革开放"],
  "type": "single"
}
```

### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "question": "中国共产党的根本宗旨是（）",
    "answer": "A"
  }
}
```

### 错误响应

```json
{
  "code": 1,
  "message": "未找到答案"
}
```

## 开发指南

### 环境要求

- JDK 21+（当前使用 JDK 25 编译目标为 21）
- Maven 3.9+
- 有效的 LLM API Key（公司内部 OpenAI 兼容服务）

### 构建与运行

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动服务（配置通过 .env 文件或环境变量注入）
mvn spring-boot:run

# 或先编译再运行
mvn clean package
java -jar target/ocs-agent-1.0.0.jar
```

### 验证 API

```bash
# 单选题
curl -X POST http://localhost:3000/api/answer \
  -H "Content-Type: application/json" \
  -d '{"question":"中国共产党的根本宗旨是（）","options":["A. 为人民服务","B. 实现共产主义","C. 依法治国","D. 改革开放"],"type":"single"}'

# 判断题（text/plain Content-Type）
curl -X POST http://localhost:3000/api/answer \
  -H "Content-Type: text/plain" \
  -d '{"question":"中国共产党是中国工人阶级的先锋队。","options":["A. 正确","B. 错误"],"type":"judgement"}'

# 填空题
curl -X POST http://localhost:3000/api/answer \
  -H "Content-Type: application/json" \
  -d '{"question":"马克思主义最鲜明的品格是（）。","options":[],"type":"completion"}'
```
