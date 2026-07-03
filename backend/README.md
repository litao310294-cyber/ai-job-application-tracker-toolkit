# Backend（后端服务）

Spring Boot backend for `ai-job-screening-agent`.

## Capabilities（当前能力）

- `POST /api/job/analyze`：岗位 AI 分析主流程。
- DeepSeek OpenAI-compatible API 调用。
- Redis cache：缓存岗位分析结果。
- MySQL persistence：保存 `job_record`、`job_analysis`、`job_feedback`、`user_profile`、`user_profile_document`、`user_profile_chunk`。
- Profile RAG-Lite：关键词检索用户画像和可选历史反馈 chunk。
- Feedback loop：保存用户主动填写的投递反馈。

## Requirements（环境要求）

- Java 17
- Maven 3.9+
- MySQL 8
- Redis 7
- DeepSeek API Key

## Configuration（配置）

参考仓库根目录 `.env.example`：

```text
DEEPSEEK_API_KEY=
MYSQL_URL=jdbc:mysql://localhost:3306/ai_job_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USERNAME=root
MYSQL_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

真实 API Key、密码、Cookie、Token 不应提交到 GitHub。

## Run（启动）

从仓库根目录启动依赖：

```bash
docker compose up -d
```

初始化数据库：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -proot ai_job_agent < backend/src/main/resources/schema.sql
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

## Notes（说明）

如果 `DEEPSEEK_API_KEY` 为空、LLM 未启用、远程调用失败或 JSON 解析失败，`/api/job/analyze` 会返回 fallback analysis，避免主流程直接失败。

当前 RAG-Lite 是关键词检索，不是向量数据库方案。后端不直接访问招聘平台，只处理 Userscript 在用户主动浏览页面时提交到本地服务的岗位字段。
