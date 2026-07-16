# AI 求职 Agent API

默认地址：`http://localhost:8080`。以下响应字段以当前 Spring Boot DTO 为准。

## 基础

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/health` | 无 | `{status, service}` |

## Profile

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/profile/current` | 无 | 当前 `UserProfileResponse` |
| POST | `/api/profile/manual` | JSON `UserProfileRequest` | 更新后的画像 |
| POST | `/api/profile/resume/upload` | multipart `file` | `{success,fileName,pageCount,textLength,documentId,chunkCount,message}` |
| POST | `/api/profile/reindex?includeHistory=false` | Query 可选 | `ProfileReindexResponse` |
| GET | `/api/profile/search?query=Java&topK=5` | Query `query` 必填 | `{exists,query,topK,chunks[]}` |
| POST | `/api/profile/generate-scoring-config` | 无 | `UserScoringConfigResponse` |
| GET | `/api/profile/scoring-config` | 无 | `UserScoringConfigResponse` |
| POST | `/api/profile/scoring-config/confirm` | 无 | `UserScoringConfigResponse` |

## Job / Analysis

### `POST /api/job/capture`

接收浏览器采集的 `StructuredJobInfo`，写入 `job_record`。

```json
{
  "jobTitle":"AI 应用开发实习生",
  "companyName":"示例公司",
  "salary":"300-500 元/天",
  "city":"北京",
  "education":"本科",
  "experience":"不限",
  "skills":["Java","RAG"],
  "jobTags":["实习"],
  "rawJD":"完整可见 JD",
  "extractionMode":"VUE"
}
```

响应：`{success, jobRecordId, created, completenessScore, extractionMode}`。

### `POST /api/job/analyze`

请求字段：`jobTitle, companyName, salary, city, schedule, duration, jobText, ruleScore, ruleConclusion, capturedJobRecordId`。响应字段：`jobRecordId, taskId, status, decision, score, direction, reasons[], risks[], resumeMatches[], interviewFocus[], suggestedMessage, profileRag`。

`profileRag` 包含 `retrievalMode`、`chunkCount` 和 `chunks[]`；每个 chunk 包含 `id,title,content,sourceType,chunkType,semanticScore,keywordScore,chunkWeight,baseScore,finalScore`。

### `GET /api/job/records?limit=20`

返回已有分析记录摘要 `JobRecordSummary[]`。

### `POST /api/job/debug-capture`

调试接口，接收 `{structuredJobInfo,bossHelperJobData,sourceUrl}`，只打印采集 JSON，不写业务表。

## History / Feedback

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/jobs/recent?limit=20` | Query 可选 | `JobHistoryRecord[]` |
| GET | `/api/jobs/{jobRecordId}` | Path | 单条 `JobHistoryRecord` |
| GET | `/api/jobs/search?keyword=Java&limit=20` | Query 可选 | `JobHistoryRecord[]` |
| GET | `/api/jobs/match?companyName=&jobTitle=` | Query 可选 | `JobHistoryRecord[]` |
| POST | `/api/job/feedback` | JSON `JobFeedbackRequest`，需 `jobRecordId` 或 `taskId` | `201 JobFeedbackResponse` |
| GET | `/api/job/feedback?jobRecordId=1` | Query 必填 | `JobFeedbackResponse[]` |

## Trace

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/api/trace/{taskId}` | Path `taskId` | 按执行顺序返回 `JobAnalysisTrace[]` |

每条 Trace 包含 `id,taskId,jobRecordId,stage,inputData,outputData,latencyMs,createdTime`。
