# User Profile Scoring Config & RAG-Lite Design

本文档记录 AI Job Screening Agent 的用户画像、评分配置和 RAG-Lite 设计。当前最新状态：第 5、6、7 阶段已经完成并验收；第 8 阶段进入项目收口、文档化和演示化。

## 当前实现状态

- 第 1 阶段：已完成 `user_profile` 表、`POST /api/profile/manual`、`GET /api/profile/current`。
- 第 2 阶段：已完成 AI 辅助生成 `user_scoring_config`，并支持确认配置。
- 第 3 阶段：userscript 已能加载后端 scoring config，并与默认规则做去重合并；后端配置是个性化加权，不覆盖默认 Java/AI 关键词库。
- 第 4 阶段：已完成 Profile RAG-Lite `POST /api/profile/reindex` 和 `GET /api/profile/search`。
- 第 4.1 阶段：reindex 已改成按 profileName 幂等重建，同一画像最终保留稳定 document/chunk。
- 第 5 阶段：RAG-Lite 已接入 `/api/job/analyze`，并在 response 中返回 profileRag 命中证据；userscript 已展示“画像命中证据”。
- 第 6 阶段：已完成历史记录查询、历史分析 / 投递反馈进入 RAG-Lite、userscript 展示历史记录。
- 第 7 阶段：已完成岗位字段抽取优化、后端字段清洗、历史匹配优化，旧脏 companyName 不再进入 RAG chunk。

仍未实现：

- PDF 简历上传和解析。
- embedding。
- Redis Vector / Milvus / 向量数据库。
- Rerank。
- 多用户登录。
- 自动投递或自动发送消息。
- 读取 BOSS Cookie / Token。
- 访问 BOSS 非公开接口。

## 设计目标

项目需要把原本写在 userscript 和 Prompt 里的个人背景抽离出来，形成可保存、可确认、可检索的用户画像体系。

目标链路：

```text
用户画像录入
  -> AI 生成 scoring config
  -> 用户确认 config
  -> userscript 本地规则评分加载 config
  -> reindex 生成 RAG-Lite chunk
  -> /api/job/analyze 检索 profile chunk
  -> DeepSeek 基于岗位 + 规则评分 + 用户画像资料分析
  -> 分析和反馈落库
  -> 历史反馈反哺 RAG-Lite
```

## 数据表

### user_profile

保存 default 用户画像：

- target_roles
- preferred_cities
- skills
- projects
- positive_keywords
- negative_keywords
- hard_reject_keywords
- schedule_preference
- manual_text

### user_scoring_config

保存 AI 生成并由用户确认的评分配置：

- config_json
- generated_by
- confirmed

`config_json` 包含：

- targetRoles
- preferredCities
- positiveKeywords
- negativeKeywords
- hardRejectKeywords
- scheduleRiskKeywords
- roleWeights
- skillWeights
- riskWeights

### user_profile_document

保存按 profileName 生成的画像文档。

### user_profile_chunk

保存可检索 chunk。当前 sourceType 包括：

- manual_profile
- scoring_config
- job_history
- feedback_history

## scoring config 融合原则

后端 scoring config 不替代 userscript 默认规则，只作为个性化补充。

合并方式：

- positiveKeywords = 默认正向关键词 ∪ 后端正向关键词。
- negativeKeywords = 默认负向关键词 ∪ 后端负向关键词。
- hardRejectKeywords = 默认硬拒关键词 ∪ 后端硬拒关键词。
- scheduleRiskKeywords = 默认出勤风险词 ∪ 后端出勤风险词。
- weights 可以覆盖同名项，但不能删除默认权重。

这样可以保留 Java 后端、服务端、Spring Boot、Spring Cloud、MyBatis、MySQL、Redis、MQ、AI 应用、大模型、LLM、Agent、RAG、Prompt、知识库等基础识别能力。

## RAG-Lite reindex

`POST /api/profile/reindex` 按 profileName 幂等重建：

1. 查询 default 用户画像和 scoring config。
2. 如果 `includeHistory=true`，额外读取历史分析和投递反馈。
3. 删除旧 chunk。
4. 删除旧 document。
5. 生成新的 document。
6. 切分并保存新的 chunk。

必须先删 chunk 再删 document，因为 chunk 依赖 document；这样可以避免残留孤儿 chunk 或后续外键约束问题。

## RAG-Lite search

`GET /api/profile/search?query=Java%20Redis%20RAG&topK=5` 使用关键词检索 user_profile_chunk。当前不是向量召回，也没有 embedding。

返回字段包括：

- id
- title
- content
- score
- sourceType

## 接入 /api/job/analyze

RAG-Lite 只在 Redis 未命中路径中触发。

检索 query 由岗位信息拼接：

```text
jobTitle + city + schedule + duration + ruleScore + ruleConclusion + jobText
```

命中 chunk 后，后端把 topK 用户画像资料拼入 DeepSeek Prompt：

```text
【用户画像检索资料】
资料1：
标题：...
内容：...
来源：...
匹配分：...
```

Prompt 约束：

- resumeMatches 优先基于画像资料中的项目、技能和偏好。
- 没有出现在画像资料中的经历不能编造。
- risks 可结合排斥方向、出勤要求和岗位要求判断。
- suggestedMessage 应结合真实技能，如 Java、Redis、RAG、Agent、Tool Calling。

## profileRag response

`/api/job/analyze` 已返回可选字段 `profileRag`：

```json
{
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
```

userscript 已在 AI 深度核验结果区域展示“画像命中证据”。如果旧缓存没有 profileRag，前端继续正常展示原 AI 结果。

## 历史反馈进入 RAG-Lite

第 6 阶段已支持 `includeHistory=true`：

```bash
curl -X POST "http://localhost:8080/api/profile/reindex?includeHistory=true"
```

历史数据进入 RAG-Lite 前会做字段清洗，避免把 JD 句子误当成公司名写入 chunk。

## 字段清洗和历史匹配

第 7 阶段已完成基础优化：

- userscript 优先从右侧详情标题区和公司/HR 区域抽取 jobTitle/companyName。
- 前端显示 titleSource/companySource，方便排查。
- 后端保存 job_record 前使用 JobFieldSanitizer 清洗脏 companyName 和 jobTitle。
- `/api/jobs/match` 优先 companyName 精确匹配和 jobTitle 精确/包含匹配。
- 历史 RAG chunk 使用 cleanedCompanyName，不再直接拼 raw companyName。

限制：页面结构变化仍可能影响字段抽取，后续需要持续维护抽取规则。

## 为什么普通评分不走 RAG

普通评分是页面实时交互，需要低延迟和稳定性。RAG-Lite 只在用户点击 AI 深度核验后使用，避免频繁请求后端和 LLM。

## 后续规划

- 手动画像编辑页。
- PDF 简历解析。
- embedding 检索。
- 向量数据库。
- 面试复盘进入 RAG-Lite。
- Dashboard。
