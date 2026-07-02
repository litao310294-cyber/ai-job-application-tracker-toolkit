# AI Job Screening Agent 用户画像评分配置与 RAG-Lite Codex 开发文档

本文档用于记录 Codex 分阶段开发状态，避免后续继续开发时偏离项目主线。

## 项目主线

AI Job Screening Agent / BOSS 求职 Agent 是一个面向 Java 后端 / AI 应用开发实习求职场景的用户画像驱动岗位筛选 Agent。

核心链路：

```text
BOSS 当前页面可见 DOM
  -> userscript 本地规则评分
  -> 用户主动点击 AI 深度核验
  -> Spring Boot /api/job/analyze
  -> Redis 分析缓存
  -> Profile RAG-Lite 检索用户画像 chunk
  -> DeepSeek 结构化分析
  -> MySQL 落库
  -> userscript 展示 AI 结果和 profileRag 证据
  -> 用户保存投递反馈
  -> 历史反馈 reindex 进入 RAG-Lite
```

## 必须遵守的边界

- 不做自动投递。
- 不做自动发送消息。
- 不读取 BOSS Cookie / Token。
- 不访问 BOSS 非公开接口。
- 不绕过验证码或登录校验。
- userscript 只读取当前页面可见 DOM 文本。
- AI 分析只作为辅助建议，最终是否投递由用户人工决定。
- 不写入真实 API Key、密码或隐私数据。

## 已完成阶段

### 第 1 阶段：用户画像数据源

已完成：

- `user_profile` 表。
- `POST /api/profile/manual`。
- `GET /api/profile/current`。
- default 用户画像保存与查询。

未做：

- PDF 上传。
- RAG chunk。
- AI 生成 scoring config。
- `/api/job/analyze` 改造。

### 第 2 阶段：AI 辅助生成用户评分配置

已完成：

- `user_scoring_config` 表。
- `POST /api/profile/generate-scoring-config`。
- `GET /api/profile/scoring-config`。
- `POST /api/profile/scoring-config/confirm`。
- 复用已有 DeepSeek / LLM Client。
- AI 输出 JSON 基础校验和 fallback。

### 第 3 阶段：userscript 接入 scoring config

已完成：

- userscript 加载后端用户画像评分配置。
- 面板显示评分配置来源：后端用户画像 / 默认兜底。
- mergeScoringConfig 做去重合并，而不是简单替换。
- 后端配置只做个性化加权，不覆盖默认 Java/AI 基础规则。

### 第 4 阶段：Profile RAG-Lite reindex/search

已完成：

- `user_profile_document`。
- `user_profile_chunk`。
- `POST /api/profile/reindex`。
- `GET /api/profile/search?query=Java%20Redis%20RAG&topK=5`。
- 关键词检索，不使用 embedding 或向量数据库。

### 第 4.1 阶段：reindex 幂等性

已完成：

- reindex 按 profileName 幂等重建。
- 先删除 chunk，再删除 document。
- 同一个 default 画像不会无限追加 document/chunk。
- search 行为保持不变。

### 第 5 阶段：RAG 接入 analyze + profileRag 前端展示

已完成：

- Profile RAG-Lite 已接入 `/api/job/analyze`。
- Redis 未命中时构造 profileQuery：

```text
jobTitle + city + schedule + duration + ruleScore + ruleConclusion + jobText
```

- 后端检索 user_profile_chunk topK，并拼入 DeepSeek Prompt。
- Redis cache key 加入 profileVersion，避免画像变化后继续命中旧分析。
- `/api/job/analyze` response 返回 `profileRag` 命中证据：
  - enabled
  - profileVersion
  - query
  - chunkCount
  - chunks
  - reason
- userscript 已展示“画像命中证据”模块。
- cache hit 时也能返回缓存中的 profileRag。

### 第 6 阶段：历史记录与反馈进入 RAG-Lite

已完成：

- 历史记录查询接口。
- userscript 展示历史记录。
- 投递反馈保存到 `job_feedback`。
- `reindex?includeHistory=true` 支持把历史分析和反馈加入 RAG-Lite。
- 历史反馈 chunk 可通过 `/api/profile/search` 检索。

### 第 7 阶段：字段抽取质量和历史匹配质量优化

已完成：

- userscript 优化 jobTitle/companyName 抽取优先级。
- 前端展示 `titleSource` / `companySource` 调试信息。
- 后端保存前用 `JobFieldSanitizer` 清洗脏 companyName/jobTitle。
- `/api/jobs/match` 优化 companyName + jobTitle 匹配质量。
- 历史 RAG chunk 使用 cleanedCompanyName，旧脏 companyName 不再进入 chunk。

已知限制：

- 页面结构变化仍可能影响字段抽取。
- 如果公司名确实无法识别，优先显示“未识别公司”，不乱填 JD 句子。

### 第 8 阶段：项目收口包装 / 文档化 / 演示化

当前阶段目标：

- 更新 README。
- 补齐架构文档。
- 补齐 API 文档。
- 补齐演示脚本。
- 补齐简历和面试材料。
- 明确已完成能力、当前限制和后续规划。

本阶段只修改 README 和 docs，不改业务代码。

## 当前 API 清单

- `GET /api/health`
- `POST /api/job/analyze`
- `GET /api/job/records`
- `POST /api/job/feedback`
- `GET /api/job/feedback`
- `GET /api/jobs/recent`
- `GET /api/jobs/{jobRecordId}`
- `GET /api/jobs/search`
- `GET /api/jobs/match`
- `POST /api/profile/manual`
- `GET /api/profile/current`
- `POST /api/profile/generate-scoring-config`
- `GET /api/profile/scoring-config`
- `POST /api/profile/scoring-config/confirm`
- `POST /api/profile/reindex`
- `GET /api/profile/search`

## 当前未做能力

- PDF 上传和解析。
- embedding。
- Redis Vector。
- Milvus。
- Rerank。
- 多用户登录。
- 自动投递。
- 自动发送消息。
- 读取 BOSS Cookie / Token。
- 访问 BOSS 非公开接口。

## 后续开发建议

1. 先做手动画像编辑页，让 default 用户画像更容易维护。
2. 再做 PDF 简历解析，把简历内容转为 user_profile。
3. 当 chunk 规模变大后，再考虑 embedding 和向量检索。
4. 将面试复盘加入 RAG-Lite，让面试经验也能反哺岗位判断。
5. 增加 Dashboard，展示投递数、回复率、约面率、方向分布和拒绝原因。
