# API Reference（接口文档）

默认后端地址：`http://localhost:8080`

示例中的岗位、公司和反馈均为 mock 数据，不包含真实隐私信息。

## Health Check（健康检查）

```bash
curl http://localhost:8080/api/health
```

Response:

```json
{
  "status": "ok",
  "service": "ai-job-screening-agent-backend"
}
```

## Analyze Job（岗位分析）

`POST /api/job/analyze`

Redis 未命中时，后端会保存 `job_record`，检索 Profile RAG-Lite，调用 DeepSeek API 或 fallback，并保存 `job_analysis`。

Request:

```json
{
  "jobTitle": "Java Backend Intern",
  "companyName": "Demo Tech",
  "salary": "200-300/day",
  "city": "Shanghai",
  "schedule": "5 days/week",
  "duration": "3 months",
  "jobText": "Java Spring Boot MySQL Redis AI application RAG-Lite",
  "ruleScore": 85,
  "ruleConclusion": "recommended"
}
```

Response:

```json
{
  "jobRecordId": 1,
  "taskId": "generated-task-id",
  "status": "success",
  "decision": "recommended",
  "score": 86,
  "direction": "Java backend + AI application",
  "reasons": ["Matches Java backend and Redis experience."],
  "risks": ["Confirm internship schedule and mentor support."],
  "resumeMatches": ["Java", "Spring Boot", "Redis"],
  "interviewFocus": ["API design", "cache design"],
  "suggestedMessage": "Hello, I am interested in this Java backend internship...",
  "profileRag": {
    "enabled": true,
    "profileVersion": "profile-version-hash",
    "query": "Java Backend Intern Shanghai 5 days/week 3 months 85 recommended",
    "chunkCount": 1,
    "chunks": [
      {
        "id": 12,
        "title": "Skills",
        "content": "Java, Spring Boot, MySQL, Redis, RAG-Lite",
        "score": 8,
        "sourceType": "manual_profile"
      }
    ],
    "reason": null
  }
}
```

If `DEEPSEEK_API_KEY` is empty, LLM is disabled, the remote call fails, or response parsing fails, the endpoint returns a fallback analysis instead of failing the main flow.

## Feedback（投递反馈）

`POST /api/job/feedback`

```json
{
  "jobRecordId": 1,
  "taskId": "generated-task-id",
  "applyStatus": "applied",
  "chatStatus": "replied",
  "interviewStatus": "not_scheduled",
  "feedbackNote": "Role direction matches my backend projects.",
  "rejectReason": ""
}
```

`GET /api/job/feedback?jobRecordId=1`

Returns feedback records for a job.

## Job History（岗位历史）

- `GET /api/jobs/recent?limit=20`
- `GET /api/job/records?limit=20`
- `GET /api/jobs/{jobRecordId}`
- `GET /api/jobs/search?keyword=Redis&limit=10`
- `GET /api/jobs/match?companyName=Demo%20Tech&jobTitle=Java%20Backend%20Intern`

These endpoints are used by the userscript to show recent records and similar historical jobs.

## Profile（用户画像）

- `POST /api/profile/manual`：保存 default 用户画像。
- `GET /api/profile/current`：读取当前用户画像。
- `POST /api/profile/generate-scoring-config`：基于用户画像生成评分配置。
- `GET /api/profile/scoring-config`：读取评分配置。
- `POST /api/profile/scoring-config/confirm`：确认评分配置。
- `POST /api/profile/reindex?includeHistory=true`：重建 RAG-Lite document/chunk，可选择纳入历史分析和反馈。
- `GET /api/profile/search?query=Java%20Redis&topK=5`：搜索 Profile RAG-Lite chunk。

## Notes（说明）

- 当前只支持默认 `default` profile。
- RAG-Lite 是关键词检索，不是向量数据库方案。
- 真实密钥、密码、Cookie、Token 不应写入请求示例或提交到仓库。
