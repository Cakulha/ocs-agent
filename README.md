# OCS Agent

> AI 驱动的在线课程自动答题后端服务

OCS Agent 是基于 Spring Boot 的后端服务，为 [OCS (Online Course Script)](https://github.com/ocsjs/ocsjs) 前端提供 AI 自动答题能力。它接收前端传递的题目参数，调用大模型推理，解析模型回复后返回格式化答案。

支持单选题、多选题、判断题和填空题四种题型。开启 OCS「图片题优化」后，支持图片题（多模态）。

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Maven | 3.9+ |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- 有效的 LLM API Key（公司内部 OpenAI 兼容服务）

### 配置

支持两种配置方式：

1. **`.env` 文件（推荐）**：在项目根目录创建 `.env` 文件（参考 `.env.example`），服务启动时自动加载：

```bash
cp .env.example .env
# 编辑 .env 填入真实配置
```

```properties
LLM_API_KEY=your-api-key
LLM_ENDPOINT=https://your-llm-host/v1/chat/completions
LLM_MODEL=your-model-name
LLM_TIMEOUT_SECONDS=60
LLM_THINKING=false
SERVER_PORT=3000
```

2. **环境变量**：直接通过环境变量注入（优先级高于 `.env` 文件）：

```bash
export LLM_API_KEY=your-api-key
```

`src/main/resources/application.yml` 中的默认值（endpoint 和 model 无内置默认值，必须通过 `.env` 或环境变量提供）：

```yaml
server:
  port: ${SERVER_PORT:3000}

llm:
  api-key: ${LLM_API_KEY:${API_KEY:}}
  endpoint: ${LLM_ENDPOINT:}
  model: ${LLM_MODEL:}
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:60}
  thinking: ${LLM_THINKING:false}
```

### 构建与运行

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动服务
mvn spring-boot:run

# 或打包后运行
mvn clean package
java -jar target/ocs-agent-1.0.0.jar
```

### 验证接口

```bash
# 单选题
curl -X POST http://localhost:3000/api/answer \
  -H "Content-Type: application/json" \
  -d '{"question":"中国共产党的根本宗旨是（）","options":["A. 为人民服务","B. 实现共产主义","C. 依法治国","D. 改革开放"],"type":"single"}'

# 判断题
curl -X POST http://localhost:3000/api/answer \
  -H "Content-Type: application/json" \
  -d '{"question":"中国共产党是中国工人阶级的先锋队。","options":["A. 正确","B. 错误"],"type":"judgement"}'
```

## API 文档

### POST /api/answer

接收题目并返回 AI 答案。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | `string` | 是 | 题目文本 |
| `options` | `string[]` | 是 | 选项数组，如 `["A. 为人民服务"]`；填空题传 `[]` |
| `type` | `string` | 否 | `single` / `multiple` / `judgement` / `completion` |
| `images` | `string[]` | 否 | 图片题：base64 data URL 数组，顺序对应题目中 `[图片N]` 占位符 |

**成功响应**

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

**无答案响应**

```json
{
  "code": 1,
  "message": "未找到答案"
}
```

详细 API 规范见 [.docs/AI-TIKU-API.md](.docs/AI-TIKU-API.md)。

## 架构

```
OCS 前端脚本 → POST /api/answer → AnswerController
                                      ↓
                               AnswerService (编排)
                                      ↓
                           LlmClient → LLM API
                                      ↓
                           AnswerParser (按题型解析)
                                      ↓
                              ← AnswerResponse ←
```

各组件说明见 [.docs/DESIGN.md](.docs/DESIGN.md)。

## 项目结构

```
ocs-agent/
  pom.xml
  src/main/java/com/ocs/agent/
    controller/AnswerController.java    # API 入口
    model/                              # 请求/响应 DTO、题型枚举
    service/
      AnswerService.java                # 业务编排
      LlmClient.java                    # LLM HTTP 客户端
      AnswerParser.java                 # 答案解析器
    config/LlmConfig.java               # LLM 配置属性
  src/main/resources/application.yml    # 服务配置
  .env                                  # 本地环境变量配置（不提交，参考 .env.example）
  .env.example                          # 环境变量配置模板
  src/test/java/.../AnswerParserTest.java  # 单元测试
  .docs/
    DESIGN.md                           # 设计文档
    AI-TIKU-API.md                      # API 接口文档
    ocs-config.json                     # OCS 前端题库配置
```

## 题型答案格式

| 题型 | LLM 输出格式 | 示例 |
|------|-------------|------|
| 单选题 | 选项字母 | `"B"` |
| 多选题 | 字母用 `#` 连接 | `"A#C#D"` |
| 判断题 | `"正确"` 或 `"错误"` | `"正确"` |
| 填空题 | 答案文本 | `"为人民服务"` |