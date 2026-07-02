# AI Job Screening Agent Backend

Spring Boot backend for AI Job Screening Agent / BOSS 求职 Agent.

当前后端已经不再是最小 mock 服务，而是项目主链路的一部分：负责 AI 深度核验、DeepSeek 调用、MySQL 落库、Redis 缓存、用户画像配置、Profile RAG-Lite、历史记录查询和投递反馈闭环。

## Current Capabilities

- `GET /api/health` 健康检查。
- `POST /api/job/analyze` 岗位 AI 深度核验。
- DeepSeek OpenAI-compatible LLM 接入。
- MySQL 落库：`job_record`、`job_analysis`、`job_feedback`。
- Redis 分析结果缓存，避免相同岗位重复调用 LLM 和重复落库。
- 用户画像保存与查询：`POST /api/profile/manual`、`GET /api/profile/current`。
- 用户画像 scoring config：生成、查询和确认。
- Profile RAG-Lite：`POST /api/profile/reindex`、`GET /api/profile/search`。
- RAG-Lite 已接入 `/api/job/analyze`。
- `/api/job/analyze` 返回 `profileRag` 命中证据。
- 历史记录查询：`GET /api/job/records`、`GET /api/jobs/recent`、`GET /api/jobs/{jobRecordId}`、`GET /api/jobs/search`、`GET /api/jobs/match`。
- 投递反馈：`POST /api/job/feedback`、`GET /api/job/feedback`。
- 历史分析 / 投递反馈可通过 `reindex?includeHistory=true` 反哺 RAG-Lite。
- 字段清洗和历史匹配优化，降低脏 companyName / jobTitle 对历史匹配和 RAG chunk 的影响。

## Requirements

- Java 17
- Maven 3.9+
- MySQL 8
- Redis 7+
- DeepSeek API Key

## Environment Variables

参考仓库根目录 `.env.example`：

```text
DEEPSEEK_API_KEY=
MYSQL_URL=
MYSQL_USERNAME=
MYSQL_PASSWORD=
REDIS_HOST=
REDIS_PORT=
REDIS_PASSWORD=
```

不要把真实 API Key、数据库密码、Redis 密码提交到 GitHub。

## LLM Config

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

如果 `DEEPSEEK_API_KEY` 为空、LLM 未启用、调用失败或 JSON 解析失败，`/api/job/analyze` 会走 fallback，不让接口直接 500。

## MySQL Setup

Create database:

```sql
CREATE DATABASE IF NOT EXISTS ai_job_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

Execute schema:

```bash
mysql -u root -p ai_job_agent < src/main/resources/schema.sql
```

也可以在 DataGrip 中打开 `src/main/resources/schema.sql` 并执行。

## Redis Setup

Start local Redis:

```bash
docker run -d --name ai-job-agent-redis -p 6379:6379 redis:7-alpine
```

If another local Redis is already listening on `localhost:6379`, it can be reused.

Check Redis:

```bash
redis-cli ping
```

Expected:

```text
PONG
```

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

Expected:

```json
{
  "status": "ok",
  "service": "ai-job-screening-agent-backend"
}
```

## Basic Test Flow

### 1. Save User Profile

```bash
curl -X POST "http://localhost:8080/api/profile/manual" \
  -H "Content-Type: application/json" \
  -d '{"targetRoles":"Java后端, AI应用开发","preferredCities":"北京","skills":"Java, Spring Boot, MySQL, Redis, RAG, Agent","projects":"示例项目：Spring Boot 后端、Redis 缓存、RAG 检索。","positiveKeywords":"Java, Spring Boot, Redis, RAG","negativeKeywords":"测试, 运维, 实施","hardRejectKeywords":"电话销售, 纯测试","schedulePreference":"优先每周4天以上，3个月以上。","manualText":"目标是 Java 后端 / AI 应用开发实习。"}'
```

### 2. Generate and Confirm Scoring Config

```bash
curl -X POST "http://localhost:8080/api/profile/generate-scoring-config"
curl -X POST "http://localhost:8080/api/profile/scoring-config/confirm"
```

### 3. Reindex Profile RAG-Lite

```bash
curl -X POST "http://localhost:8080/api/profile/reindex"
```

### 4. Analyze Job

```bash
curl -X POST "http://localhost:8080/api/job/analyze" \
  -H "Content-Type: application/json" \
  -d '{"jobTitle":"大模型应用开发实习生","companyName":"示例科技","salary":"200-300元/天","city":"北京","schedule":"5天/周","duration":"3个月","jobText":"Java Spring Boot MySQL Redis RAG Agent Tool Calling 大模型应用开发","ruleScore":85,"ruleConclusion":"优先投"}'
```

Expected response includes:

- `jobRecordId`
- `taskId`
- `decision`
- `score`
- `direction`
- `profileRag`

### 5. Save Feedback

```bash
curl -X POST "http://localhost:8080/api/job/feedback" \
  -H "Content-Type: application/json" \
  -d '{"jobRecordId":1,"applyStatus":"已投递","chatStatus":"已沟通","interviewStatus":"未约面","feedbackNote":"岗位方向匹配，准备继续跟进。","rejectReason":""}'
```

### 6. Reindex with History

```bash
curl -X POST "http://localhost:8080/api/profile/reindex?includeHistory=true"
```

### 7. Search Profile Chunks

```bash
curl "http://localhost:8080/api/profile/search?query=Java%20Redis%20RAG&topK=5"
```

## Compliance Boundary

Backend does not access BOSS directly. It only receives data submitted by the local userscript after the user opens a page and triggers analysis.

Project boundary:

- 不读取 BOSS Cookie / Token。
- 不访问 BOSS 非公开接口。
- 不绕过验证码或登录校验。
- 不自动投递。
- 不自动发送消息。
- AI 分析只作为辅助建议，最终是否投递由用户人工决定。

## Current Limitations

- 单用户 default 画像。
- RAG-Lite 使用关键词检索，不是 embedding。
- 没有 PDF 简历上传和解析。
- 没有 Redis Vector、Milvus、Rerank。
- 字段清洗是兜底规则，页面结构变化时仍可能需要维护 userscript 抽取逻辑。
