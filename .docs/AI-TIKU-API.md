# AI 题库接口文档

> 本文档描述 OCS (Online Course Script) 前端与后端 AI 题库服务之间的通信协议。
> 适用于对接学习通/超星等网课平台的自动答题功能。

## 一、整体流程

```
前端 (OCS 脚本)                          后端 (AI 服务)
  │                                        │
  │  POST /api/answer                      │
  │  { question, options, type }           │──▶ 调用大模型推理
  │                                        │
  │  ◀── 200 OK                            │
  │  { code: 0, data: { question, answer }}│
  │                                        │
  │  解析 answer → 匹配选项 → 自动勾选     │
```

## 二、请求

### 接口地址

```
POST {your-server}/api/answer
```

### 请求头

| Header | Value | 说明 |
|--------|-------|------|
| Content-Type | 无（前端不主动发送） | body 始终为 `JSON.stringify(data)` 序列化的字符串。部分浏览器/油猴管理器可能自动补 `text/plain;charset=UTF-8`，**后端应同时兼容 `application/json` 和 `text/plain`** |

### 请求体

```json
{
  "question": "中国共产党的根本宗旨是（）",
  "options": [
    "A. 为人民服务",
    "B. 实现共产主义",
    "C. 依法治国",
    "D. 改革开放"
  ],
  "type": "single"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | `string` | ✅ 是 | 题目文本。由前端从 DOM 提取，已做清理（去除了冗余题型标签、多余空格/换行等） |
| `options` | `string[]` | ✅ 是 | 选项文本数组。**仅当 `type` 为 `single` / `multiple` / `judgement` 时携带**；填空题 (`completion`) 时为空数组 `[]` |
| `type` | `string` | ❌ 否 | 题目类型，枚举值见下表 |

### 题目类型枚举

| 值 | 含义 | 说明 |
|----|------|------|
| `"single"` | 单选题 | 有且仅有一个正确答案 |
| `"multiple"` | 多选题 | 有一个或多个正确答案 |
| `"judgement"` | 判断题 | 只有两个选项（正确/错误） |
| `"completion"` | 填空题/简答 | 需填写文字答案 |

### 补充说明

- `question` 在前端经过以下处理：
  1. 从 DOM 提取 `innerText`
  2. (若题目含 `<img>` 标签，图片链接会被转换为不可见文本节点（fontSize=0），因此 `question` 中可能包含图片 URL)当前版本开发不考虑图片的情况
  3. 执行 `StringUtils.nowrap()`（多小题时用 `\n` 保留换行，否则替换为空格）
  4. 执行 `StringUtils.nospace()`（多个连续空格合并为一个）
  5. 执行 `removeRedundantWords()`（按配置移除"【单选题】""（多选题）"等冗余前缀）
- `options` 中每个元素的格式为 `"{字母}. {选项内容}"`，例如 `"A. 为人民服务"`，字母与内容之间有一个空格

## 三、响应

### 成功响应

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

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `code` | `number` | ✅ 是 | 状态码。`0` 表示成功，非 `0` 表示无答案/出错 |
| `message` | `string` | ❌ 否 | 可选，错误时的提示信息 |
| `data.question` | `string` | ✅ 是 | 返回的题目原文（可与前端传入的一致，也可省略） |
| `data.answer` | `string` | ✅ 是 | 答案文本，格式依题型而定 |

### 无答案时的响应

```json
{
  "code": 1,
  "message": "未找到答案"
}
```

当 `code !== 0` 时，前端不会尝试匹配答案，直接进入下一题。

### 各题型答案格式

#### 单选题 (`single`)

返回单个选项字母：

```json
"data": { "answer": "B" }
```

或返回完整选项内容：

```json
"data": { "answer": "实现共产主义" }
```

前端会依次尝试三种匹配方式：
1. **精确匹配**：去除标点/空格/全角半角差异后与选项文本比对
2. **相似度匹配**：阈值 > 0.6 即判定成功
3. **纯字母兜底**：A/B/C/D 单字符直接匹配

#### 多选题 (`multiple`)

返回多个选项字母，用分隔符连接（**必须是字符串，不是数组**）：

```json
"data": { "answer": "A#C#D" }
```

支持的分隔符：`===`、`#`、`---`、`###`、`|`、`;`、`；`

前端会将答案按分隔符拆分为 `["A", "C", "D"]`，然后逐个匹配选项。

#### 判断题 (`judgement`)

返回布尔关键词，前端会进行关键词匹配：

```json
"data": { "answer": "正确" }
```

- 匹配"正确/对/是/√/true/yes/1"等 → 勾选"正确"选项
- 匹配"错误/错/否/×/false/no/0"等 → 勾选"错误"选项

#### 填空题 (`completion`)

返回填写的文本内容：

```json
"data": { "answer": "为人民服务" }
```

前端会将答案填入对应的 `<textarea>` 或富文本编辑器。

## 四、完整示例

### 示例 1：单选题

**请求：**

```json
POST /api/answer
Content-Type: application/json

{
  "question": "新民主主义革命的三大经济纲领是（）",
  "options": [
    "A. 没收封建阶级的土地归农民所有",
    "B. 没收蒋宋孔陈四大家族垄断资本归新民主主义国家所有",
    "C. 保护民族工商业",
    "D. 平均地权"
  ],
  "type": "single"
}
```

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "question": "新民主主义革命的三大经济纲领是（）",
    "answer": "ABC"
  }
}
```

### 示例 2：判断题

**请求：**

```json
POST /api/answer
Content-Type: application/json

{
  "question": "中国共产党是中国工人阶级的先锋队，同时是中国人民和中华民族的先锋队。",
  "options": ["A. 正确", "B. 错误"],
  "type": "judgement"
}
```

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "question": "中国共产党是中国工人阶级的先锋队，同时是中国人民和中华民族的先锋队。",
    "answer": "正确"
  }
}
```

### 示例 3：填空题

**请求：**

```json
POST /api/answer
Content-Type: application/json

{
  "question": "马克思主义最鲜明的品格是（）。",
  "options": [],
  "type": "completion"
}
```

**响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "question": "马克思主义最鲜明的品格是（）。",
    "answer": "人民性"
  }
}
```

## 五、题库配置 (JSON)

将以下配置粘贴到 OCS 脚本的「通用 → 全局设置 → 题库配置」中即可使用：

```json
[
  {
    "name": "AI智能题库",
    "homepage": "http://localhost:3000",
    "url": "http://localhost:3000/api/answer",
    "method": "post",
    "contentType": "json",
    "type": "fetch",
    "headers": {},
    "data": {
      "question": "${title}",
      "options": {
        "handler": "return (env)=>env.options?.split('\\n')"
      },
      "type": {
        "handler": "return (env)=> env.type === 'single' ? 'single' : env.type === 'multiple' ? 'multiple' : env.type === 'judgement' ? 'judgement' : env.type === 'completion' ? 'completion' : undefined"
      }
    },
    "handler": "return (res)=> res.code === 0 ? [res.data.question, res.data.answer] : undefined"
  }
]
```

### 关键字段说明

| 字段 | 说明 |
|------|------|
| `url` | 你的后端接口地址 |
| `data.question` | 使用 `${title}` 占位符，前端会自动替换为题目文本 |
| `data.options.handler` | 将前端传入的选项字符串（`\n` 分隔）拆分为数组 |
| `data.type.handler` | 将题目类型透传给后端（也可自行修改映射逻辑） |
| `handler` | 响应解析器，将后端返回格式转换为 `[题目, 答案]` 格式 |

## 六、注意事项

1. **无需 token**：此为自用服务，接口不需要认证
2. **无需 times 字段**：OCS 不会传递时间戳参数
3. **超时设置**：默认搜题超时时间为 60 秒（可在 OCS 高级设置中调整），请确保你的 AI 服务在此时间内返回
4. **线程数**：默认同时搜 1 题（可在 OCS 设置中调整为最多 3 题并发）
5. **域名白名单**：如果使用油猴脚本，需在脚本头部 `@connect` 中添加你的域名，或安装全域名通用版本
6. **答案分隔符**：多选题答案必须用分隔符连接为字符串，不要用 JSON 数组
