# AI Job Screening Agent Backend

Minimal Spring Boot backend for the `feature/ai-job-screening-agent` branch.

这是 `feature/ai-job-screening-agent` 分支中的最小 Java 后端服务，用于先打通 AI Job Screening Agent 的接口闭环。

当前版本实现了 LLM 分析 v0.1 和 MySQL 落库：

- 连接 MySQL 保存岗位记录和分析结果
- 连接 Redis 缓存岗位分析结果，避免相同请求重复调用 LLM 和重复落库
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

## MySQL Config / MySQL 配置

Create database:

创建数据库：

```sql
create database if not exists ai_job_agent
  default character set utf8mb4
  collate utf8mb4_unicode_ci;
```

Execute table schema:

执行建表 SQL：

```bash
mysql -u root -p ai_job_agent < src/main/resources/schema.sql
```

You can also open `src/main/resources/schema.sql` in DataGrip and run it against the `ai_job_agent` database.

也可以在 DataGrip 中打开 `src/main/resources/schema.sql`，连接到 `ai_job_agent` 数据库后执行。

Configure MySQL with environment variables:

使用环境变量配置 MySQL：

PowerShell:

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/ai_job_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your_mysql_password"
```

macOS / Linux:

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/ai_job_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="your_mysql_password"
```

Do not commit real database passwords into the repository.

不要把真实数据库密码提交到仓库。

## Redis Config / Redis 配置

Default Redis connection:

默认 Redis 连接：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3s

job-analysis:
  cache:
    enabled: true
    ttl-hours: 72
    key-prefix: ai-job-agent:analysis:
```

Start a local Redis container:

启动本地 Redis 容器：

```bash
docker run -d --name ai-job-agent-redis -p 6379:6379 redis:7-alpine
```

If you already have a local Redis container, for example `redis-vector`, it can be reused as long as port `6379` is available and `redis-cli ping` returns `PONG`.

如果本地已有 Redis 容器，例如 `redis-vector`，只要 `6379` 端口可用，并且 `redis-cli ping` 返回 `PONG`，可以直接复用。

Check Redis:

检查 Redis：

```bash
docker exec -it redis-vector redis-cli ping
```

View cache keys:

查看缓存 key：

```bash
docker exec -it redis-vector redis-cli keys "ai-job-agent:analysis:*"
```

Do not use `flushall` for this project.

不要为了本项目执行 `flushall` 清空 Redis 全库。

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

Every `/api/job/analyze` call saves one row into `job_record` and one row into `job_analysis`, including fallback responses.

每次调用 `/api/job/analyze` 都会向 `job_record` 保存 1 条岗位记录，并向 `job_analysis` 保存 1 条分析记录；fallback 响应也会保存。

When Redis cache is enabled and the same request is repeated, the second call returns the cached `JobAnalyzeResponse` directly. It will not call DeepSeek and will not insert duplicate rows into MySQL.

启用 Redis 缓存后，同一个请求第二次调用会直接返回缓存中的 `JobAnalyzeResponse`，不会调用 DeepSeek，也不会向 MySQL 重复插入记录。

### GET /api/job/records

```bash
curl "http://localhost:8080/api/job/records?limit=20"
```

If there is no data, the response is:

如果暂无数据，响应为：

```json
[]
```

### POST /api/job/feedback

Save user follow-up feedback for a job record:

保存某条岗位记录的后续投递反馈：

```bash
curl -X POST http://localhost:8080/api/job/feedback \
  -H "Content-Type: application/json" \
  -d '{
    "jobRecordId": 2,
    "applyStatus": "已投递",
    "chatStatus": "已沟通",
    "interviewStatus": "未约面",
    "feedbackNote": "岗位方向匹配，准备继续跟进。",
    "rejectReason": ""
  }'
```

Expected response:

响应示例：

```json
{
  "id": 1,
  "jobRecordId": 2,
  "applyStatus": "已投递",
  "chatStatus": "已沟通",
  "interviewStatus": "未约面",
  "feedbackNote": "岗位方向匹配，准备继续跟进。",
  "rejectReason": "",
  "createdAt": "2026-07-01T12:00:00"
}
```

### GET /api/job/feedback

Query feedback for a job record:

查询某条岗位记录的反馈：

```bash
curl "http://localhost:8080/api/job/feedback?jobRecordId=2"
```

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

To test database persistence:

测试落库：

1. Send the `POST /api/job/analyze` request above.
2. Create a `GET` request in Apifox.
3. URL: `http://localhost:8080/api/job/records?limit=20`
4. The response should include the analyzed job summary.

发送上面的 `POST /api/job/analyze` 后，再请求 `GET /api/job/records?limit=20`，应该能看到刚刚分析过的岗位摘要。

To test Redis cache hit:

测试 Redis 缓存命中：

1. Ensure Redis is running and `redis-cli ping` returns `PONG`.
2. Send the same `POST /api/job/analyze` request once.
3. Send the exact same request again.
4. The second response should be faster.
5. Check MySQL: `job_record` and `job_analysis` should not add duplicate rows for the second identical request.
6. Check Redis keys:

```bash
docker exec -it redis-vector redis-cli keys "ai-job-agent:analysis:*"
```

步骤：

1. 确认 Redis 已启动，并且 `redis-cli ping` 返回 `PONG`。
2. 第一次发送相同的 `POST /api/job/analyze` 请求。
3. 第二次再次发送完全相同的请求。
4. 第二次响应应该更快。
5. 检查 MySQL：第二次相同请求不应让 `job_record` 和 `job_analysis` 增加重复记录。
6. 使用上面的 `redis-cli keys` 命令查看缓存 key。

To test feedback:

测试反馈闭环：

1. Call `GET /api/job/records?limit=20` and copy one `jobRecordId`.
2. Create a `POST` request in Apifox.
3. URL: `http://localhost:8080/api/job/feedback`
4. Header: `Content-Type: application/json`
5. Body:

```json
{
  "jobRecordId": 2,
  "applyStatus": "已投递",
  "chatStatus": "已沟通",
  "interviewStatus": "未约面",
  "feedbackNote": "岗位方向匹配，准备继续跟进。",
  "rejectReason": ""
}
```

6. Query feedback with `GET http://localhost:8080/api/job/feedback?jobRecordId=2`.

步骤：

1. 调用 `GET /api/job/records?limit=20`，复制一个 `jobRecordId`。
2. 在 Apifox 新建 `POST` 请求。
3. URL 填 `http://localhost:8080/api/job/feedback`。
4. Header 设置 `Content-Type: application/json`。
5. 填入上面的 JSON Body。
6. 再调用 `GET http://localhost:8080/api/job/feedback?jobRecordId=2` 查询反馈列表。

## DataGrip Check / 使用 DataGrip 验证

After calling `/api/job/analyze`, run:

调用 `/api/job/analyze` 后，在 DataGrip 中执行：

```sql
select * from job_record order by id desc limit 20;
select * from job_analysis order by id desc limit 20;
select * from job_feedback order by id desc limit 20;
```

Expected result:

预期结果：

- `job_record` has one new row for the request.
- `job_analysis` has one new row linked by `job_record_id`.
- `job_analysis.status` is `success` when LLM succeeds, otherwise `fallback`.
- `job_feedback` has one new row after calling `POST /api/job/feedback`.

- `job_record` 会新增 1 条岗位记录。
- `job_analysis` 会新增 1 条分析记录，并通过 `job_record_id` 关联。
- LLM 成功时 `job_analysis.status` 为 `success`，否则为 `fallback`。
- 调用 `POST /api/job/feedback` 后，`job_feedback` 会新增 1 条反馈记录。

## Notes / 说明

This version is intentionally small. It is used to verify the request/response contract, MySQL persistence, and LLM analysis flow before adding retrieval features.

当前版本刻意保持很小，只用于验证前后端请求/响应字段、MySQL 落库和 LLM 分析流程。后续再按需要接入知识检索能力。
