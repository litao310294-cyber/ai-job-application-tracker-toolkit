# API Reference

后端默认地址：`http://localhost:8080`。

示例中的公司、岗位和反馈均为 mock 数据，不包含真实隐私信息。

## GET /api/health

用途：检查后端服务是否启动。

示例请求：

```bash
curl http://localhost:8080/api/health
```

示例返回：

```json
{
  "status": "ok",
  "service": "ai-job-screening-agent-backend"
}
```

注意事项：不依赖 MySQL 业务数据。

## POST /api/job/analyze

用途：对一个岗位进行 AI 深度核验。Redis 未命中时会保存 job_record，检索 Profile RAG-Lite，调用 DeepSeek，保存 job_analysis，并缓存完整 response。

请求体：

```json
{
  "jobTitle": "大模型应用开发实习生",
  "companyName": "示例科技",
  "salary": "200-300元/天",
  "city": "北京",
  "schedule": "5天/周",
  "duration": "3个月",
  "jobText": "Java Spring Boot MySQL Redis RAG Agent Tool Calling 大模型应用开发",
  "ruleScore": 85,
  "ruleConclusion": "优先投"
}
```

示例请求：

```bash
curl -X POST "http://localhost:8080/api/job/analyze" \
  -H "Content-Type: application/json" \
  -d '{"jobTitle":"大模型应用开发实习生","companyName":"示例科技","salary":"200-300元/天","city":"北京","schedule":"5天/周","duration":"3个月","jobText":"Java Spring Boot MySQL Redis RAG Agent Tool Calling 大模型应用开发","ruleScore":85,"ruleConclusion":"优先投"}'
```

示例返回：

```json
{
  "jobRecordId": 1,
  "taskId": "mock-task-id",
  "status": "success",
  "decision": "优先投",
  "score": 86,
  "direction": "Java后端 + AI应用",
  "reasons": ["岗位方向与 Java 后端和 AI 应用经历匹配"],
  "risks": ["需要确认实习周期和导师投入"],
  "resumeMatches": ["Java", "Spring Boot", "Redis", "RAG", "Agent"],
  "interviewFocus": ["项目中的缓存设计", "RAG 检索链路"],
  "suggestedMessage": "您好，我对这个岗位很感兴趣...",
  "profileRag": {
    "enabled": true,
    "profileVersion": "5bcd6fa7...",
    "query": "大模型应用开发实习生 北京 ...",
    "chunkCount": 5,
    "chunks": [
      {
        "id": 66,
        "title": "技能栈",
        "content": "Java, Spring Boot, MySQL, Redis, RAG, Agent, Tool Calling",
        "score": 10,
        "sourceType": "manual_profile"
      }
    ],
    "reason": null
  }
}
```

注意事项：

- 如果 Redis 命中，会直接返回缓存 response。
- 如果 LLM 调用失败，会返回 fallback status，不让主接口直接 500。
- `profileRag` 是可选字段，旧缓存或旧后端返回可能没有。

## POST /api/job/feedback

用途：保存用户对岗位分析结果的后续反馈。

请求体：

```json
{
  "jobRecordId": 1,
  "taskId": "mock-task-id",
  "applyStatus": "已投递",
  "chatStatus": "已沟通",
  "interviewStatus": "未约面",
  "feedbackNote": "岗位方向匹配，准备继续跟进。",
  "rejectReason": ""
}
```

示例请求：

```bash
curl -X POST "http://localhost:8080/api/job/feedback" \
  -H "Content-Type: application/json" \
  -d '{"jobRecordId":1,"applyStatus":"已投递","chatStatus":"已沟通","interviewStatus":"未约面","feedbackNote":"岗位方向匹配，准备继续跟进。","rejectReason":""}'
```

示例返回：

```json
{
  "id": 1,
  "jobRecordId": 1,
  "applyStatus": "已投递",
  "chatStatus": "已沟通",
  "interviewStatus": "未约面",
  "feedbackNote": "岗位方向匹配，准备继续跟进。",
  "rejectReason": "",
  "createdAt": "2026-07-02T10:00:00"
}
```

注意事项：如果没有 jobRecordId，可以带 taskId，后端会尝试反查 jobRecordId。

## GET /api/job/feedback

用途：查询某个岗位记录的反馈列表。

请求参数：

- `jobRecordId`：岗位记录 ID。

示例请求：

```bash
curl "http://localhost:8080/api/job/feedback?jobRecordId=1"
```

示例返回：

```json
[
  {
    "id": 1,
    "jobRecordId": 1,
    "applyStatus": "已投递",
    "chatStatus": "已沟通",
    "interviewStatus": "未约面",
    "feedbackNote": "岗位方向匹配，准备继续跟进。",
    "rejectReason": "",
    "createdAt": "2026-07-02T10:00:00"
  }
]
```

## GET /api/jobs/recent

用途：查询最近岗位分析记录。

请求参数：

- `limit`：返回数量，默认通常为 20。

示例请求：

```bash
curl "http://localhost:8080/api/jobs/recent?limit=20"
```

示例返回：

```json
[
  {
    "jobRecordId": 1,
    "jobTitle": "大模型应用开发实习生",
    "companyName": "示例科技",
    "salary": "200-300元/天",
    "city": "北京",
    "ruleScore": 85,
    "ruleConclusion": "优先投",
    "aiDecision": "优先投",
    "aiScore": 86,
    "aiDirection": "Java后端 + AI应用",
    "status": "success",
    "createdAt": "2026-07-02T10:00:00"
  }
]
```

## GET /api/job/records

用途：早期版本的最近分析记录查询接口。当前仍保留用于兼容，推荐新展示逻辑优先使用 `/api/jobs/recent`。

请求参数：

- `limit`：返回数量，默认 20，建议范围 1 到 100。

示例请求：

```bash
curl "http://localhost:8080/api/job/records?limit=20"
```

示例返回：

```json
[
  {
    "jobRecordId": 1,
    "jobTitle": "大模型应用开发实习生",
    "companyName": "示例科技",
    "salary": "200-300元/天",
    "city": "北京",
    "ruleScore": 85,
    "ruleConclusion": "优先投",
    "aiDecision": "优先投",
    "aiScore": 86,
    "aiDirection": "Java后端 + AI应用",
    "status": "success",
    "createdAt": "2026-07-02T10:00:00"
  }
]
```

## GET /api/jobs/{jobRecordId}

用途：查询单条岗位记录详情。

示例请求：

```bash
curl "http://localhost:8080/api/jobs/1"
```

示例返回：

```json
{
  "jobRecordId": 1,
  "jobTitle": "大模型应用开发实习生",
  "companyName": "示例科技",
  "salary": "200-300元/天",
  "city": "北京",
  "ruleScore": 85,
  "ruleConclusion": "优先投",
  "aiDecision": "优先投",
  "aiScore": 86,
  "aiDirection": "Java后端 + AI应用",
  "status": "success"
}
```

注意事项：不存在时应返回明确错误或空结果，不应影响其他接口。

## GET /api/jobs/search

用途：按关键词搜索历史岗位记录。

请求参数：

- `keyword`：搜索词。
- `limit`：返回数量。

示例请求：

```bash
curl "http://localhost:8080/api/jobs/search?keyword=RAG&limit=10"
```

示例返回：

```json
[
  {
    "jobRecordId": 1,
    "jobTitle": "大模型应用开发实习生",
    "companyName": "示例科技",
    "aiDecision": "优先投",
    "aiDirection": "Java后端 + AI应用"
  }
]
```

## GET /api/jobs/match

用途：根据当前岗位的 companyName + jobTitle 匹配历史记录。

请求参数：

- `companyName`：公司名，可为空或未识别。
- `jobTitle`：岗位名。

示例请求：

```bash
curl "http://localhost:8080/api/jobs/match?companyName=示例科技&jobTitle=大模型应用开发实习生"
```

示例返回：

```json
[
  {
    "jobRecordId": 1,
    "jobTitle": "大模型应用开发实习生",
    "companyName": "示例科技",
    "aiDecision": "优先投",
    "createdAt": "2026-07-02T10:00:00"
  }
]
```

注意事项：

- 公司名精确匹配优先。
- 岗位名精确匹配和包含匹配优先。
- 脏 companyName 会被降低权重或过滤。
- 匹配不到时返回空数组。

## GET /api/profile/scoring-config

用途：查询当前 default 用户画像评分配置。

示例请求：

```bash
curl "http://localhost:8080/api/profile/scoring-config"
```

示例返回：

```json
{
  "exists": true,
  "profileName": "default",
  "confirmed": true,
  "configJson": {
    "targetRoles": ["Java后端", "AI应用开发"],
    "positiveKeywords": ["Java", "Spring Boot", "Redis", "RAG"]
  }
}
```

注意事项：不存在时返回 `exists=false` 或类似空状态，不应 500。

## POST /api/profile/reindex

用途：按 default 用户画像幂等重建 RAG-Lite document/chunk。

请求参数：

- `includeHistory`：可选，`true` 时把历史分析和反馈纳入 RAG-Lite。

示例请求：

```bash
curl -X POST "http://localhost:8080/api/profile/reindex?includeHistory=true"
```

示例返回：

```json
{
  "success": true,
  "profileName": "default",
  "documentId": 12,
  "chunkCount": 9
}
```

注意事项：

- reindex 是幂等重建，同一 profileName 不会无限追加旧 document/chunk。
- 删除顺序是先 chunk 后 document。

## GET /api/profile/search

用途：搜索 Profile RAG-Lite chunk。

请求参数：

- `query`：检索文本。
- `topK`：返回数量。

示例请求：

```bash
curl "http://localhost:8080/api/profile/search?query=Java%20Redis%20RAG&topK=5"
```

示例返回：

```json
[
  {
    "id": 66,
    "title": "技能栈",
    "content": "Java, Spring Boot, MySQL, Redis, RAG, Agent, Tool Calling",
    "score": 10,
    "sourceType": "manual_profile"
  }
]
```

## Profile 初始化相关接口

项目还包含以下画像初始化接口：

- `POST /api/profile/manual`：保存 default 用户画像。
- `GET /api/profile/current`：查询当前画像。
- `POST /api/profile/generate-scoring-config`：调用 LLM 生成评分配置。
- `POST /api/profile/scoring-config/confirm`：确认当前评分配置。

这些接口通常在首次使用或修改画像时调用，不是每次岗位分析都调用。
