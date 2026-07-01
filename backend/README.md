# AI Job Screening Agent Backend

Minimal Spring Boot backend for the `feature/ai-job-screening-agent` branch.

这是 `feature/ai-job-screening-agent` 分支中的最小 Java 后端服务，用于先打通 AI Job Screening Agent 的接口闭环。

当前版本只提供 mock 分析结果：

- 不连接 MySQL
- 不连接 Redis
- 不接 Spring AI
- 不接 RAG
- 不调用大模型 API

## Requirements / 环境要求

- Java 17
- Maven 3.9+

## Start / 启动服务

From the `backend/` directory:

```bash
mvn spring-boot:run
```

The default port is `8080`.

默认端口为 `8080`。

## API

### GET /api/health

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "ok",
  "service": "ai-job-screening-agent-backend"
}
```

### POST /api/job/analyze

```bash
curl -X POST http://localhost:8080/api/job/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "jobTitle": "Java后端实习生",
    "companyName": "示例科技",
    "salary": "200-300元/天",
    "city": "北京",
    "schedule": "3天/周",
    "duration": "3个月",
    "jobText": "负责 Java 后端接口开发，使用 Spring Boot、MySQL、Redis。",
    "ruleScore": 76,
    "ruleConclusion": "可投"
  }'
```

Expected response fields:

```json
{
  "taskId": "generated-uuid",
  "status": "mocked",
  "decision": "可投",
  "score": 76,
  "direction": "Java后端",
  "reasons": [],
  "risks": [],
  "resumeMatches": [],
  "interviewFocus": [],
  "suggestedMessage": "..."
}
```

## Notes / 说明

The first version is intentionally small. It is only used to verify request and response contracts before adding persistence, AI analysis, or retrieval features.

第一版刻意保持很小，只用于验证前后端请求与响应字段。后续再按需要接入持久化、AI 分析或知识检索能力。
