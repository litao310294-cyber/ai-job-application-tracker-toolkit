# Resume Project Description

## 项目名称

AI Job Screening Agent / BOSS 求职 Agent

## 2 行版项目描述

面向 Java 后端 / AI 应用开发实习求职场景，设计并实现一个用户画像驱动的岗位筛选 Agent。项目通过 Tampermonkey 用户脚本读取页面可见岗位信息，结合 Spring Boot、DeepSeek、MySQL、Redis 和 RAG-Lite，完成岗位评分、AI 深度核验、投递反馈和历史经验反哺。

## 简历 Bullet

- 设计 BOSS 页面岗位匹配度面板，基于可见 DOM 文本抽取岗位、薪资、城市、出勤周期等字段，并通过本地规则完成 Java 后端 / AI 应用方向初筛。
- 搭建 Spring Boot 后端，接入 DeepSeek OpenAI-compatible API，实现 `/api/job/analyze` 结构化分析，并使用 Redis 对同岗位同画像版本分析结果做缓存去重。
- 使用 MySQL 沉淀 job_record、job_analysis、job_feedback、user_profile、user_scoring_config 和 RAG-Lite chunk，形成岗位分析与投递反馈闭环。
- 实现 Profile RAG-Lite，将用户画像、历史分析和投递反馈按关键词检索后拼入 LLM Prompt，并在前端展示 profileRag 命中证据，提升分析可解释性。

## 技术栈

- 前端辅助：Tampermonkey userscript、DOM 文本抽取、GM_xmlhttpRequest。
- 后端：Java 17、Spring Boot 3.5、Maven、JDK HttpClient、Jackson。
- 数据：MySQL 8、Spring JDBC、Redis、StringRedisTemplate。
- AI：DeepSeek OpenAI-compatible API、结构化 JSON Prompt、RAG-Lite 关键词检索。
- 工程：字段清洗、缓存 key 设计、幂等 reindex、历史匹配优化。

## 面试 1 分钟讲法

这个项目是我为个人求职场景做的 AI 岗位筛选 Agent。它不会替用户自动投递，而是读取 BOSS 当前页面已经展示的岗位信息，先在浏览器里做规则评分；用户主动点击后，再调用本地 Spring Boot 后端进行 AI 深度核验。后端会用 Redis 避免重复分析，用 MySQL 保存岗位、分析和反馈，并把用户画像和历史反馈做成 RAG-Lite chunk，在 DeepSeek 分析时提供个性化上下文。最后前端会展示 AI 结论、画像命中证据、历史记录和投递反馈入口。

## 面试 3 分钟讲法

我做这个项目的动机是，实习投递多了以后，岗位是否匹配、有没有回复、为什么放弃、面试要重点准备什么，很容易散在截图和聊天记录里。于是我把它拆成三个层次。

第一层是 userscript。本地脚本只读取当前浏览器页面可见 DOM，不访问平台非公开接口。它抽取岗位标题、公司、薪资、城市、出勤周期和 JD 文本，做 Java 后端、AI 应用、客户端、GIS、.NET 等方向识别，并展示规则评分。

第二层是 Spring Boot 后端。用户点击 AI 深度核验后，前端把当前岗位信息发给 `/api/job/analyze`。后端先检查 Redis，如果同一用户画像版本和同一岗位已经分析过，就直接返回缓存；未命中时保存 job_record，检索 Profile RAG-Lite，再调用 DeepSeek，最后保存 job_analysis 并缓存完整 response。

第三层是反馈闭环。用户可以在页面保存投递、沟通、面试状态和备注。后续执行 reindex 时，历史分析和投递反馈会进入 RAG-Lite。这样下一次分析相似岗位时，AI 不只看当前 JD，也能参考我的技能、项目和过去反馈。

这个项目目前没有做向量数据库，而是先用关键词 RAG-Lite，因为当前画像数据规模小、结构明确，关键词检索可解释、实现成本低，也更适合快速验证完整闭环。

## 高频追问

### 为什么普通评分不走 RAG？

普通评分在页面切换岗位时实时更新，需要低延迟、稳定和可解释。RAG 和 LLM 放在用户主动点击“AI 深度核验”之后，避免每次页面变化都请求后端。

### RAG-Lite 和向量 RAG 有什么区别？

RAG-Lite 当前把用户画像和历史反馈切成文本 chunk，用关键词和简单评分召回。向量 RAG 会把文本转成 embedding，并用向量数据库检索语义相似内容。本项目第一版数据量小，所以先用 RAG-Lite 验证链路。

### Redis 缓存怎么设计？

缓存的是 `/api/job/analyze` 的完整 response。key 包含 profileVersion 和岗位请求 hash。profileVersion 来自用户画像文档内容版本，因此用户画像 reindex 后，相同岗位会生成新的缓存 key。

### Tool Calling / Agent 体现在哪里？

当前没有做自动执行外部动作的 Tool Calling。项目中的 Agent 体现在“感知当前岗位、读取用户画像、调用分析服务、展示证据、保存反馈、反哺画像”的闭环。后续如果扩展，可以把搜索历史、保存反馈、生成复盘等后端接口封装为更明确的工具调用。

### 如何保证合规？

userscript 只读取当前页面可见 DOM 文本；不读取 Cookie / Token；不访问 BOSS 非公开接口；不绕过验证码；不自动投递；不自动发送消息。AI 只给建议，最终是否投递由用户人工决定。

### 如果换用户怎么适配？

当前是单用户 default 画像。换用户时，可以扩展 profileName/userId，把 user_profile、user_scoring_config、RAG-Lite document/chunk 和缓存 profileVersion 按用户隔离。

### 历史反馈如何反哺？

投递反馈保存在 `job_feedback`。执行 `POST /api/profile/reindex?includeHistory=true` 后，后端会把历史分析和反馈整理为 chunk。后续 `/api/job/analyze` Redis 未命中时会检索这些 chunk，并把结果拼入 DeepSeek Prompt。

## 项目不足

- 当前只有单用户 default，没有登录和权限体系。
- RAG-Lite 仍是关键词检索，不是 embedding 或向量库。
- 没有 PDF 简历解析。
- 字段抽取依赖页面结构，页面改版时可能需要维护。
- AI 结论需要人工复核，不能作为自动决策。

## 后续规划

- 增加手动画像编辑页。
- 支持 PDF 简历解析和结构化画像补全。
- 引入 embedding 检索和 rerank。
- 把面试复盘纳入 RAG-Lite。
- 增加 Dashboard，展示投递数、回复率、约面率、方向分布和拒绝原因。
