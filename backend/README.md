# AI Job Screening Agent Backend

Minimal Spring Boot backend for the `feature/ai-job-screening-agent` branch.

这是 `feature/ai-job-screening-agent` 分支中的最小 Java 后端服务，用于先打通 AI Job Screening Agent 的接口闭环。

当前版本实现了 LLM 分析 v0.1：

- 不连接 MySQL
- 不连接 Redis
- 不接 Spring AI
- 不接 RAG
- 使用 OpenAI-compatible Chat Completions 接口调用 DeepSeek
- 如果未配置 API Key、关闭 LLM 或调用失败，会自动返回 fallback 结果，不让接口 500

## Requirements / 环境要求

- Java 17
- Maven 3.9+

## LLM Config / 大模型配置

The default provider is DeepSeek OpenAI-compatible API.

默认使用 DeepSeek 的 OpenAI-compatible API。

`src/main/resources/application.yml`:

```yaml
llm:
  provider: deepseek
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat
  timeout-seconds: 30
  enabled: true
```

Set the API key before starting the backend.

启动前设置环境变量。

PowerShell:

```powershell
$env:DEEPSEEK_API_KEY="your_deepseek_api_key"
```

macOS / Linux:

```bash
export DEEPSEEK_API_KEY="your_deepseek_api_key"
```

Do not commit real API keys into the repository.

不要把真实 API Key 提交到仓库。

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
  "status": "success",
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

`status` values:

- `success`: LLM call succeeded and returned valid JSON.
- `fallback`: LLM is disabled, API key is empty, the call failed, timed out, returned non-2xx, or JSON parsing failed.

`status` 取值：

- `success`：LLM 调用成功，并返回了合法 JSON。
- `fallback`：LLM 未启用、API Key 为空、调用失败、超时、非 2xx 或 JSON 解析失败。

## Apifox Test / 使用 Apifox 测试

1. Start the backend with `mvn spring-boot:run`.
2. Create a `POST` request in Apifox.
3. URL: `http://localhost:8080/api/job/analyze`
4. Header: `Content-Type: application/json`
5. Body type: JSON
6. Paste this body:

```json
{
  "jobTitle": "AI 应用开发后端实习生（Java/Go方向）",
  "companyName": "示例科技",
  "salary": "200-300元/天",
  "city": "北京",
  "schedule": "每周3-4天",
  "duration": "3个月",
  "jobText": "负责 AI 应用开发、后端接口开发、模型接口对接，使用 Java、Go、Docker、微服务。",
  "ruleScore": 76,
  "ruleConclusion": "可投"
}
```

If `DEEPSEEK_API_KEY` is set correctly, the response should have `status: "success"`.

如果正确配置了 `DEEPSEEK_API_KEY`，响应中的 `status` 应为 `"success"`。

If the key is empty or the LLM call fails, the response will still use the same response fields, but `status` will be `"fallback"`.

如果 key 为空或 LLM 调用失败，接口仍会返回同样字段，但 `status` 会是 `"fallback"`。

## Notes / 说明

This version is intentionally small. It is used to verify the request/response contract and LLM analysis flow before adding persistence or retrieval features.

当前版本刻意保持很小，只用于验证前后端请求/响应字段和 LLM 分析流程。后续再按需要接入持久化或知识检索能力。
