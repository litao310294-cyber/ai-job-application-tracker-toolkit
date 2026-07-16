# AI 求职 Agent 工程化整理

## 当前后端分析链路

```text
POST /api/job/analyze
        |
        v
JobAnalysisService（流程编排）
        |
        +--> JobRecordRepository（岗位记录/兼容 capturedJobRecordId）
        +--> JobRuleEngine（沿用请求中的规则评分结果）
        +--> JobQueryBuilder + RagRetrievalService（Hybrid/Fallback RAG）
        +--> RagContextBuilder（证据上下文与分数格式化）
        +--> JobAnalysisPromptBuilder（分析请求上下文）
        +--> LlmAnalyzer --> 现有 LlmClient/DeepSeek
        +--> ResultValidator
        +--> FallbackAnalysisService（异常/未配置降级）
        +--> AnalysisSaver --> job_analysis + Redis
```

`OpenAiCompatibleLlmClient` 的 DeepSeek HTTP 调用和原有 JSON 输出格式保持不变；`RagRetrievalService` 的 VECTOR、KEYWORD、HYBRID、FALLBACK_KEYWORD 模式保持不变。

## 前端模块取舍

仓库之前没有独立的 React/Vue 前端工程，只有 Userscript 和后端。因此新增 `frontend/` 静态工作台：

| 模块 | 处理 | 说明 |
|---|---|---|
| Dashboard | 保留并新增 | 聚合 `/api/jobs/recent`，显示分析数、建议投递数和平均分 |
| 简历管理/Profile Memory | 新增 | PDF 上传、画像查看、Chunk 检索、索引重建 |
| 岗位分析 | 新增 | 调用 `/api/job/analyze`，展示结果及 RAG 证据分数 |
| 历史投递 | 新增 | 使用 `/api/jobs/recent` 查看岗位和分析历史 |
| Agent Trace | 升级 | 通过 `/api/trace/{taskId}` 查看规则、RAG、Prompt、LLM、校验和保存阶段 |
| 设置 | 合并 | API Base 配置保存在浏览器 localStorage |
| 无关 Demo | 不新增 | 未发现独立 Demo 前端，不做删除性重构 |

## 约束

前端使用原生 HTML/CSS/JavaScript，无新构建链路；后端数据库、RAG、Embedding、DeepSeek 和原有接口均未重写。
