# Interview Guide

这份文档用于面试前快速复习 AI Job Screening Agent / BOSS 求职 Agent 的讲法。内容偏口语化，可以直接背诵或按自己的表达微调。

## 1. 项目一句话介绍

这是一个面向 Java 后端 / AI 应用开发实习求职场景的用户画像驱动岗位筛选 Agent：它通过浏览器 userscript 读取当前页面可见岗位信息，结合 Spring Boot、DeepSeek、MySQL、Redis 和 RAG-Lite，完成岗位初筛、AI 深度核验、投递反馈和历史经验反哺。

## 2. 1 分钟面试讲法

我做这个项目是为了解决实习投递过程中岗位多、状态乱、反馈难沉淀的问题。前端是一个 Tampermonkey userscript，只读取 BOSS 当前页面已经展示的岗位文本，先用本地规则判断岗位是否适合 Java 后端或 AI 应用方向。用户主动点击“AI 深度核验”后，请求本地 Spring Boot 后端。后端会用 Redis 判断是否已有相同岗位分析缓存，未命中时检索用户画像 RAG-Lite，再调用 DeepSeek 输出结构化判断，并把岗位、分析和投递反馈保存到 MySQL。后续 reindex 时，历史反馈也能进入 RAG-Lite，让下一次分析更贴近我的求职偏好。

## 3. 3 分钟面试讲法

这个项目可以分三层讲。

第一层是浏览器端。userscript 运行在 BOSS 页面上，只读取当前页面可见 DOM 文本，抽取岗位标题、公司、薪资、城市、出勤周期和 JD 文本。它会做一轮本地规则评分，比如 Java/Spring/MySQL/Redis、AI/Agent/RAG、大模型接口、出勤周期、社招经验等，并在右下角展示岗位匹配度。

第二层是后端分析链路。用户主动点击“AI 深度核验”后，前端把当前岗位信息发到 `/api/job/analyze`。后端先根据岗位字段和 profileVersion 生成 Redis cache key。命中缓存就直接返回；未命中时先保存 job_record，再用岗位信息检索用户画像 chunk，把命中的技能、项目、偏好、历史反馈拼进 DeepSeek Prompt，得到结构化分析结果，再保存 job_analysis 并写入 Redis。

第三层是反馈闭环。用户在页面上可以保存投递状态、沟通状态、面试状态、备注和放弃原因，这些会进入 `job_feedback`。执行 `reindex?includeHistory=true` 后，历史分析和反馈会变成 RAG-Lite chunk。这样后续分析相似岗位时，AI 不只看当前 JD，也能参考我过去为什么投、为什么放弃、哪些方向更匹配。

这个项目目前没有做向量数据库和 PDF 简历解析，第一版先用关键词 RAG-Lite，因为用户画像和历史反馈数据规模较小，可解释性也更好。

## 4. 系统架构讲法

系统分为五块：

- userscript：负责页面可见信息抽取、本地规则评分、AI 深度核验按钮、反馈保存和历史展示。
- Spring Boot：负责 API、字段清洗、LLM 调用、缓存、落库、RAG-Lite 检索和历史匹配。
- MySQL：保存岗位记录、AI 分析、投递反馈、用户画像、评分配置和 RAG-Lite chunk。
- Redis：缓存岗位分析结果，避免相同岗位重复调用 DeepSeek 和重复落库。
- DeepSeek：在用户主动触发深度核验时输出结构化岗位分析。

一句话总结：浏览器端做轻量实时判断，后端做可靠分析和长期记忆。

## 5. RAG-Lite 讲法

RAG-Lite 是这个项目的用户画像检索层。它不是向量数据库 RAG，而是把用户画像、技能、项目、偏好、历史分析和投递反馈切成 chunk，用关键词匹配召回 topK，再拼进 LLM Prompt。

这样做的原因是：当前数据规模不大，内容主要是 Java、Redis、RAG、Agent、城市、出勤偏好、放弃原因等明确关键词。关键词检索实现简单、可解释、方便调试，也能先验证完整闭环。

## 6. Redis 缓存讲法

Redis 缓存的是 `/api/job/analyze` 的完整分析 response，不是用户画像本身。

cache key 由两部分组成：

- profileVersion：用户画像当前版本，优先基于 RAG document 的内容 hash。
- jobHash：岗位请求字段的稳定 hash，包括岗位名、公司、城市、薪资、JD、规则分和规则结论。

这样同一画像版本下的相同岗位可以复用分析结果；如果用户画像重新 reindex，profileVersion 变化，相同岗位会重新分析。

## 7. MySQL 表设计讲法

核心表分三类。

岗位分析类：

- `job_record`：保存岗位标题、公司、薪资、城市、JD、规则分和规则结论。
- `job_analysis`：保存 AI 决策、AI 分数、方向、理由、风险、简历匹配点、面试关注点和建议话术。
- `job_feedback`：保存用户后续投递、沟通、面试和放弃原因。

用户画像类：

- `user_profile`：保存目标岗位、城市、技能、项目、正负关键词和手动画像文本。
- `user_scoring_config`：保存 AI 生成并由用户确认的个性化评分配置。

RAG-Lite 类：

- `user_profile_document`：保存一次 reindex 生成的画像文档。
- `user_profile_chunk`：保存可检索 chunk，包括手动画像、评分配置、历史分析和反馈。

## 8. 历史反馈闭环讲法

投递反馈不是只存起来看，而是会反哺后续分析。用户保存反馈后，执行 `POST /api/profile/reindex?includeHistory=true`，后端会把历史分析和反馈整理成 chunk。下一次分析类似岗位时，如果检索命中这些 chunk，DeepSeek 就能参考过去的投递经验，比如“某类岗位不匹配”“某个方向更适合”“某些出勤周期不接受”。

为了避免脏数据污染，后端在保存和生成历史 chunk 时会清洗 companyName 和 jobTitle，避免把“负责接口开发...”这类 JD 句子当成公司名。

## 9. 合规边界讲法

这个项目的边界很清楚：

- 只读取当前浏览器页面已经展示的 DOM 文本。
- 不读取 BOSS Cookie / Token。
- 不访问 BOSS 非公开接口。
- 不绕过验证码或登录校验。
- 不自动投递。
- 不自动发送消息。
- AI 只给分析建议，最终是否投递由用户人工决定。

所以它是个人求职跟进和辅助分析工具，不是自动操作平台的工具。

## 10. 项目不足和后续优化

当前不足：

- 只有单用户 default，没有登录和权限隔离。
- RAG-Lite 是关键词检索，不是 embedding。
- 没有 PDF 简历解析。
- 字段抽取依赖页面结构，页面改版时需要维护。
- AI 结论仍需要人工复核。

后续优化：

- 做一个手动画像编辑页。
- 接入 PDF 简历解析，把简历转成结构化 user_profile。
- 数据规模变大后接 embedding 和向量库。
- 把面试复盘也纳入 RAG-Lite。
- 做 Dashboard，看投递数、回复率、约面率、拒绝原因和方向分布。

## 11. 高频追问和回答

### 这个项目为什么不是简单套壳？

因为它不是单纯把 JD 发给大模型。项目有完整的工程链路：页面可见信息抽取、本地规则评分、字段清洗、Redis 缓存、MySQL 落库、用户画像 scoring config、RAG-Lite 检索、profileRag 证据展示、投递反馈闭环和历史反哺。LLM 只是深度核验的一环。

### 普通规则评分为什么不走 RAG？

普通评分需要在页面切换岗位时实时更新，重点是快、稳定、可解释。如果每次切换都请求后端 RAG 和 LLM，会变慢也不稳定。RAG 放在用户主动点击“AI 深度核验”后，更符合交互成本和准确性要求。

### RAG-Lite 和向量数据库 RAG 有什么区别？

RAG-Lite 是关键词检索，适合当前这种小规模、关键词明确的用户画像数据。向量数据库 RAG 会用 embedding 做语义召回，更适合大规模、表达更复杂的文档。这个项目先用 RAG-Lite 验证闭环，后续可以升级为 embedding。

### Redis 缓存 key 怎么设计？

key 包含 profileVersion 和 jobHash。profileVersion 表示当前用户画像内容版本，jobHash 表示岗位请求字段。这样画像不变、岗位不变时命中缓存；画像变了以后，即使岗位相同也会重新分析。

### 用户画像怎么适配不同用户？

当前是单用户 default。后续可以引入 userId/profileName，把 user_profile、scoring_config、document、chunk、job_record 和缓存 key 都按用户隔离。

### 历史反馈怎么反哺 RAG？

用户保存投递反馈后，反馈进入 MySQL。执行 includeHistory reindex 时，后端会把历史分析和反馈转成 chunk。后续 analyze 会检索这些 chunk，并把命中内容放进 Prompt。

### profileVersion 有什么用？

profileVersion 用来标识当前用户画像版本。它进入 Redis cache key，避免用户画像更新后仍然命中旧岗位分析缓存。

### 如何避免脏历史数据污染 RAG？

一是 userscript 尽量从右侧详情和公司信息区域抽取字段；二是后端保存前用 JobFieldSanitizer 清洗 companyName/jobTitle；三是历史 chunk 生成时使用 cleanedCompanyName，不直接拼 raw companyName。

### 如何保证合规？

只读取当前页面可见 DOM，不读取 Cookie/Token，不访问 BOSS 非公开接口，不绕过验证码，不自动投递，不自动发送消息。所有投递决策都由用户人工确认。

### 后续如果继续优化怎么做？

先做画像编辑页和 Dashboard；再做 PDF 简历解析；当数据规模变大后接 embedding 和向量检索；最后把面试复盘和多用户隔离补上。
