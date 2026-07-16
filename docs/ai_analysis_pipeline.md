# AI 求职 Agent 分析链路设计

## 1. 设计目标

岗位分析不是“把 JD 交给大模型后让它自由发挥”，而是一个受约束的证据驱动流程：

```text
岗位输入
  ↓
规则初筛
  ↓
Profile Hybrid Retrieval
  ↓
证据上下文
  ↓
结构化 LLM 分析
  ↓
结果校验与持久化
```

核心目标有三个：

1. 先用确定性规则处理学历、城市、出勤等硬约束，避免模型“解释掉”明确不符合的条件。
2. 只把与岗位相关的用户技能、项目和经历证据放入上下文，降低脱离真实简历的推断。
3. 让模型输出稳定的 JSON，由后端校验后再交给前端和历史记录使用。

## 2. 主流程

```text
JobAnalysisService
  ├─ JobRuleEngine
  ├─ JobQueryBuilder
  │    └─ RagRetrievalService
  │         ├─ VectorSearchService
  │         └─ UserProfileRagService（关键词 fallback）
  ├─ RagContextBuilder
  ├─ JobAnalysisPromptBuilder
  ├─ LlmAnalyzer
  │    └─ OpenAiCompatibleLlmClient（DeepSeek 兼容接口）
  ├─ ResultValidator
  ├─ FallbackAnalysisService
  └─ AnalysisSaver（job_analysis + Redis）
```

请求首先根据 profileVersion 生成 Redis key。缓存命中直接返回；未命中时创建或复用 `job_record`，执行规则和检索，最后保存分析结果并写入缓存。

## 3. JobAnalysisService：流程编排

`JobAnalysisService` 不负责业务细节，而负责保证步骤顺序、异常边界和返回兼容性：

1. 读取用户画像版本、检查 Redis 缓存。
2. 创建或复用岗位记录。
3. 调用 `JobRuleEngine` 得到规则结果。
4. 从结构化岗位记录或请求字段构造岗位查询。
5. 调用 `RagRetrievalService`，并将召回结果交给 `RagContextBuilder`。
6. 由 `JobAnalysisPromptBuilder` 组装分析输入。
7. 通过 `LlmAnalyzer` 调用现有 LLM 客户端。
8. 用 `ResultValidator` 校验，异常时交给 fallback。
9. 由 `AnalysisSaver` 写入 `job_analysis` 并更新 Redis。

这样做的价值是：每个策略可以单独测试，主流程不会因为某个策略实现变复杂而继续膨胀。

## 4. JobRuleEngine：确定性约束层

当前浏览器侧已经计算出 `ruleScore` 和 `ruleConclusion`，后端 `JobRuleEngine` 将这个既有规则结果纳入统一的规则边界，并识别 hard reject 结论。城市、出勤、实习周期、学历和硬性排除词属于规则层应处理的内容：

- 输入明确、可验证，适合字符串/枚举/范围判断；
- 结果需要稳定、可复现，不能因模型温度或措辞变化而改变；
- 硬约束应在语义分析前暴露为风险，供模型解释而不是由模型擅自放宽。

输出 `RuleAnalysisResult`：

```text
score          基础规则分
conclusion     规则结论
hardRejected   是否命中硬拒绝结论
reason         规则来源说明
```

规则层不负责解释复杂项目语义；“Spring Boot 项目是否能迁移到 Go 岗位”仍交给证据驱动的 LLM 分析。

## 5. RagRetrievalService：Hybrid Retrieval

检索入口使用岗位 Query，而不是把用户简历全文拼进去：

```text
岗位标题 + 城市 + 学历/经验 + 技能 + 职责 + 任职要求
```

系统并行取得两类候选：

- Vector：使用 Embedding 计算 query 与画像 chunk 的余弦相似度，并转换到 0~1 的 `semanticScore`。
- Keyword：使用标题命中、内容命中、正负关键词和 scoreHint 计算关键词证据，再归一化到 0~1 的 `keywordScore`。

Hybrid 合并以 `chunkId` 去重：

```text
baseScore = 0.7 × semanticScore + 0.3 × keywordScore
finalScore = baseScore × chunkWeight
```

Chunk 权重让“AI 求职 Agent 项目经历”在语义和关键词接近时优先于单独的“RAG 技能”词条。没有可用 Embedding 时保留关键词 fallback，不影响岗位分析可用性。

返回结果同时保留 `semanticScore`、`keywordScore`、`chunkWeight`、`baseScore` 和 `finalScore`，便于排查召回原因。

## 6. RagContextBuilder：证据上下文压缩

RAG 结果不是简单的字符串列表。`RagContextBuilder` 将每个 chunk 格式化为带标签的证据块：

```text
【用户画像检索资料】
资料 1
标题：AI 求职 Agent 项目
类型：PROJECT
内容：……
来源：resume
semanticScore：0.91
keywordScore：0.80
chunkWeight：1.30
baseScore：0.88
finalScore：1.14
```

只发送 Top-K 的相关证据有三个原因：

1. 控制 Token 和延迟，避免完整简历挤占岗位分析上下文。
2. 降低无关经历干扰，避免模型把“出现过一个词”误判为能力证明。
3. 保留标题、类型、来源和分数，使结论能够回溯到具体证据。

## 7. JobAnalysisPromptBuilder 与 Prompt 设计

`JobAnalysisPromptBuilder` 负责把岗位请求、规则结果和 RAG 上下文封装成统一的 `JobAnalysisPrompt`，使 Prompt 数据不再由 `JobAnalysisService` 拼接。为保持现有 DeepSeek 调用方式，固定的 System/User 文本仍由 `OpenAiCompatibleLlmClient` 负责发送。

当前 Prompt 的设计层次如下。

### System Prompt

- 定义角色：个人求职岗位筛选助手。
- 限定任务：根据岗位页面文本、规则结果和用户画像证据给出保守的匹配分析。
- 要求以检索到的真实用户经历为依据；检索不到的技能、项目不得编造。
- 明确只返回 JSON，不返回 Markdown、解释性前缀或代码块。

### User Prompt

包含岗位标题、公司、薪资、城市、出勤、周期、本地规则分数/结论、JD 文本和 RAG 上下文。岗位事实与用户证据分区传入，避免模型混淆“岗位要求”和“候选人已有能力”。

### Evidence Grounding

Prompt 明确要求：

- `resumeMatches` 优先引用 RAG 返回的项目、技能和偏好；
- 没有证据时使用“不确定/需要确认”，不能补写用户经历；
- 风险可以结合学历、出勤、排除方向和岗位要求判断；
- JD 较短或画像没有召回时，降低结论强度。

### JSON Schema 约束

输出固定为：

```json
{
  "decision": "优先投|可投|谨慎投|不投",
  "score": 0,
  "direction": "岗位方向",
  "reasons": [],
  "risks": [],
  "resumeMatches": [],
  "interviewFocus": [],
  "suggestedMessage": ""
}
```

这解决了三个工程问题：前端可以稳定渲染；历史表可以稳定序列化；模型输出异常不会直接污染业务数据。

## 8. LlmAnalyzer：模型适配边界

`LlmAnalyzer` 只负责判断 LLM 是否可用，并调用现有 `LlmClient`。输入是 `JobAnalysisPrompt` 中的岗位请求和 RAG 上下文；输出是 `LlmAnalyzeResult`。它不负责保存结果，也不负责重新实现 HTTP、鉴权或 JSON 清洗。

DeepSeek 调用失败、超时、API key 缺失或响应解析失败时，由编排层转入 `FallbackAnalysisService`。这样模型供应商故障不会让岗位分析接口整体不可用。

## 9. ResultValidator：稳定性边界

校验包括：

- score 存在且在 0~100；
- direction 非空；
- reasons、risks、resumeMatches、interviewFocus 均为非空数组对象；
- suggestedMessage 非空；
- LLM Client 已做 decision 枚举校验。

校验失败不会把半结构化结果交给前端，而是抛出异常并进入 fallback。前端始终获得同一组字段，避免空数组、空字符串或非法分数导致页面崩溃。

## 10. AnalysisSaver：结果一致性

分析成功或 fallback 后统一保存：

```text
job_analysis
Redis analysis cache
```

缓存 key 包含用户画像版本和岗位输入摘要，因此简历更新后不会继续命中旧画像结果。保存边界集中在一个模块，便于后续增加 trace 或审计记录而不侵入分析策略。

## 11. 为什么不使用 LLM Rerank

当前画像规模约为十到几十个 chunk，Java 内存余弦计算和关键词合并已经足够低延迟。LLM Rerank 会增加一次网络调用和 Token 成本，也会引入第二个模型判断层；当前收益不抵消复杂度。因此采用可解释的 `Hybrid Score + Chunk Importance Weight`，并保留每个中间分数用于诊断。

## 12. 面试级总结

这套链路的核心取舍是：规则保证边界，Hybrid RAG 提供证据，Prompt 约束模型，Validator 保证输出，Fallback 保证可用，Saver 保证结果可追溯。它不是让 LLM 自主决定一切，而是让模型在真实用户证据和明确业务约束内完成语义判断。
