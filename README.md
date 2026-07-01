# OCS Agent

> AI 驱动的在线课程自动答题后端服务

OCS Agent 是基于 Spring Boot 的后端服务，为 [OCS (Online Course Script)](https://github.com/murongpingcai/ocs) 前端提供 AI 自动答题能力。它接收前端传递的题目参数，调用 **Agnes 2.0 Flash** 大模型推理，解析模型回复后返回格式化答案。

支持单选题、多选题、判断题和填空题四种题型。

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Maven | 3.9+ |
| LLM | Agnes 2.0 Flash (`agnes-2.0-flash`) |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- 有效的 Agnes API Key

### 配置

在 `src/main/resources/application.yml` 中配置 LLM 连接信息，或通过环境变量 `API_KEY` 注入 API Key：

```yaml
server:
  port: 3000

llm:
  api-key: ${API_KEY}
  endpoint: https://apihub.agnes-ai.com/v1/chat/completions
  model: agnes-2.0-flash
  timeout-seconds: 60
  thinking: false
```

### 构建与运行

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动服务
AGNES_API_KEY=your-key mvn spring-boot:run

# 或打包后运行
mvn clean package
AGNES_API_KEY=your-key java -jar target/ocs-agent-1.0.0.jar
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
                           LlmClient → Agnes 2.0 Flash API
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