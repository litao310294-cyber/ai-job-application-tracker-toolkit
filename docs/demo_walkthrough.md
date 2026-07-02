# Demo Walkthrough

这是一份适合录屏、答辩或面试展示的演示脚本。所有公司、岗位和反馈内容都应使用 mock 数据。

## 1. 启动 MySQL

期望结果：数据库 `ai_job_agent` 已创建，`schema.sql` 已执行。

示例：

```sql
CREATE DATABASE IF NOT EXISTS ai_job_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

在 DataGrip 中确认存在：

- `job_record`
- `job_analysis`
- `job_feedback`
- `user_profile`
- `user_scoring_config`
- `user_profile_document`
- `user_profile_chunk`

## 2. 启动 Redis

示例：

```bash
docker run -d --name ai-job-agent-redis -p 6379:6379 redis:7-alpine
```

期望结果：

```bash
redis-cli ping
PONG
```

如果本地已有 Redis 容器，只要 `localhost:6379` 可用即可。

## 3. 启动 Spring Boot 后端

设置环境变量：

参考仓库根目录的 `.env.example`，在本机配置 `DEEPSEEK_API_KEY`、`MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD`。不要把真实值写入文档或提交到 GitHub。

启动：

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

期望结果：返回 `status=ok`。

## 4. 初始化用户画像和 RAG-Lite

保存用户画像：

```bash
curl -X POST "http://localhost:8080/api/profile/manual" \
  -H "Content-Type: application/json" \
  -d '{"targetRoles":"Java后端, AI应用开发, 大模型应用后端","preferredCities":"北京","skills":"Java, Spring Boot, MySQL, Redis, RAG, Agent, Tool Calling","projects":"黑马点评项目：Redis缓存、分布式锁、Lua秒杀；AI Job Screening Agent项目：Spring Boot后端、DeepSeek、Redis缓存、MySQL落库。","positiveKeywords":"Java, Spring Boot, MySQL, Redis, RAG, Agent, Tool Calling","negativeKeywords":"测试, 运维, 实施, 销售","hardRejectKeywords":"电话销售, 纯测试, 驻场实施","schedulePreference":"优先北京，每周4天以上，3个月以上。","manualText":"用户目标是北京 Java 后端 / AI 应用开发实习。"}'
```

生成并确认评分配置：

```bash
curl -X POST "http://localhost:8080/api/profile/generate-scoring-config"
curl -X POST "http://localhost:8080/api/profile/scoring-config/confirm"
```

重建 RAG-Lite：

```bash
curl -X POST "http://localhost:8080/api/profile/reindex"
```

期望结果：返回 `success=true`，`chunkCount` 大于 0。

## 5. 安装 userscript

1. 打开 Tampermonkey。
2. 新建用户脚本。
3. 粘贴 `userscripts/job-chat-status-export.user.js`。
4. 保存并刷新 BOSS 岗位页面。

期望结果：右下角出现“岗位匹配度”面板。

## 6. 打开 BOSS 岗位页面并查看规则评分

选择一个 mock 展示岗位，例如：

- 岗位：大模型应用开发实习生
- 城市：北京
- 薪资：200-300元/天
- 技术：Java、Spring Boot、MySQL、Redis、RAG、Agent、Tool Calling

期望结果：

- 面板展示岗位、薪资、城市、出勤周期。
- 展示规则结论、分数、方向、Excel 档位、风险点。
- 展示 `titleSource`、`companySource` 等字段来源，便于排查抽取质量。

## 7. 点击 AI 深度核验

在面板中点击“AI 深度核验”。

期望结果：

- 按钮显示分析中，完成后恢复。
- 页面展示 AI 决策、AI 分数、方向、理由、风险、简历匹配点、面试关注点和建议开场白。
- 后端日志显示 Redis cache miss 或 hit。

## 8. 查看 profileRag 画像命中证据

AI 分析结果下方会出现“画像命中证据”模块。

期望结果：

- 显示已启用。
- 显示 chunk 数量。
- 显示 profileVersion 前 8 位。
- 显示命中的技能栈、项目经历、正向关键词等 chunk。

如果是旧缓存或旧后端返回，没有 `profileRag` 时，前端应继续正常展示 AI 结果。

## 9. 保存投递反馈

在“投递反馈”区域选择：

- applyStatus：已投递
- chatStatus：已沟通
- interviewStatus：未约面
- feedbackNote：岗位方向匹配，准备继续跟进。

点击“保存投递反馈”。

期望结果：

- 页面显示“反馈已保存”。
- MySQL `job_feedback` 新增一条记录。
- `GET /api/job/feedback?jobRecordId=xxx` 能查到反馈。

## 10. 刷新页面查看历史记录

刷新岗位页面或切换回相似岗位。

期望结果：

- 历史记录区域可以看到最近分析过的岗位。
- `/api/jobs/match` 能根据 companyName 和 jobTitle 返回相关历史记录。
- 如果公司名未识别或疑似脏数据，不应把 JD 句子当作公司名优先展示。

## 11. 历史反馈进入 RAG-Lite

执行：

```bash
curl -X POST "http://localhost:8080/api/profile/reindex?includeHistory=true"
```

搜索历史反馈 chunk：

```bash
curl -G "http://localhost:8080/api/profile/search" \
  --data-urlencode "query=投递 反馈 大模型" \
  --data-urlencode "topK=5"
```

期望结果：

- 能搜到历史分析或反馈相关 chunk。
- chunk 中的 companyName 已经过清洗，不应出现“负责接口开发...”这类 JD 句子作为公司名。

## 12. 展示 Redis 缓存命中

连续两次用相同请求调用：

```bash
curl -X POST "http://localhost:8080/api/job/analyze" \
  -H "Content-Type: application/json" \
  -d '{"jobTitle":"大模型应用开发实习生","companyName":"缓存演示公司","salary":"200-300元/天","city":"北京","schedule":"5天/周","duration":"3个月","jobText":"Java Spring Boot MySQL Redis RAG Agent Tool Calling Prompt 大模型应用开发","ruleScore":85,"ruleConclusion":"优先投"}'
```

期望结果：

- 第一次 Redis cache miss，调用 LLM 并落库。
- 第二次 Redis cache hit，返回同一个 taskId/jobRecordId。
- MySQL 不重复新增同一份分析记录。

## 13. 演示收尾说明

最后强调：

- 本项目只读取当前页面可见 DOM 文本。
- 不读取 Cookie / Token。
- 不访问 BOSS 非公开接口。
- 不绕过验证码。
- 不自动投递。
- 不自动发送消息。
- AI 结果只是辅助判断，最终投递由用户人工决定。
