# AI Job Screening Agent｜用户画像初始化 + AI 生成评分配置 + RAG-Lite 开发设计文档

> 用途：本文件用于放入 Codex 项目文件中，作为后续开发时的长期参考文档。  
> 目标：让 Codex 在实现功能时，不只参照单次提示词，也能持续参照本设计文档，避免偏离项目主线。  
> 当前项目：`ai-job-application-tracker-toolkit`  
> 当前分支建议：`feature/ai-job-screening-agent` 或新建 `feature/user-profile-rag-lite`  
> 核心目标：把原来写死在前端脚本 / Prompt 中的个人背景，升级为“用户初始化画像 + AI 生成结构化评分配置 + RAG 用户画像检索”的可扩展架构。

---

## 0. 背景与问题

当前项目已经完成 v0.1 主闭环：

```text
BOSS 岗位页面
  ↓
userscript 提取当前页面可见岗位信息
  ↓
本地规则评分
  ↓
用户点击 AI 深度核验
  ↓
POST /api/job/analyze
  ↓
Spring Boot 后端
  ↓
Redis 查缓存
  ↓
未命中调用 DeepSeek
  ↓
MySQL 保存 job_record / job_analysis
  ↓
Redis 缓存分析结果
  ↓
前端展示 AI 分析
  ↓
用户保存投递反馈
  ↓
MySQL 保存 job_feedback
```

当前问题：

```text
1. 普通规则评分里，很多个人偏好和目标岗位是写死在前端 userscript 中的。
2. AI 深度核验里，用户背景 / 项目经历目前主要靠写死 Prompt。
3. 这种方式只能很好地适配当前作者本人，不适合不同用户。
4. 用户画像一变，就要改代码或改 Prompt，维护性差。
5. 后续如果想把项目包装为可复用的 AI Job Screening Agent，需要把用户背景从硬编码中抽离出来。
```

---

## 1. 新需求总目标

本次新增能力不是“重型 RAG”，而是 **用户画像驱动的 RAG-Lite + AI 评分配置生成**。

目标链路：

```text
用户第一次使用
  ↓
填写个人画像 / 未来可上传简历
  ↓
后端保存用户画像
  ↓
AI 根据用户画像生成结构化评分配置
  ↓
用户确认配置
  ↓
前端普通规则评分读取配置，本地快速评分
  ↓
非结构化用户画像切成 chunk，进入 RAG-Lite
  ↓
AI 深度核验时检索用户画像 RAG
  ↓
DeepSeek 结合岗位 JD + 规则评分 + 用户画像资料做个性化判断
  ↓
岗位分析结果和投递反馈继续沉淀
```

核心原则：

```text
普通规则评分：快、稳定、可解释，不实时调用 AI / RAG。
AI 初始化阶段：可以调用 DeepSeek，把用户自然语言画像转换成结构化评分配置。
AI 深度核验阶段：可以调用 RAG，检索用户画像资料增强 Prompt。
```

---

## 2. 总体架构

系统分成三层：

```text
第一层：结构化评分配置
- 用于 userscript 普通规则评分。
- 来源于用户画像 + AI 辅助生成 + 用户确认。
- 特点：低延迟、可解释、前端本地执行。

第二层：用户画像 RAG-Lite
- 用于 AI 深度核验前的上下文检索。
- 来源于用户手动画像、项目经历、简历文本、历史反馈等。
- 第一版先做关键词检索，不做 embedding / 向量库。

第三层：AI 深度核验
- 用岗位 JD + 规则评分 + RAG 检索资料调用 DeepSeek。
- 输出 decision、score、direction、reasons、risks、resumeMatches、interviewFocus、suggestedMessage。
```

不要做成：

```text
每个岗位普通评分都实时调用 DeepSeek。
每个岗位普通评分都实时做 RAG 检索。
让 AI 直接生成可执行 JS 打分代码。
AI 输出不校验就保存。
```

推荐做成：

```text
固定评分引擎 + AI 生成配置。
通用 Prompt + RAG 动态检索用户资料。
```

---

## 3. 阶段拆分

### 阶段 1：用户初始化画像

目标：先把用户信息从前端脚本硬编码迁移到后端保存。

#### 前端新增入口

在 userscript 面板中新增：

```text
初始化个人画像
```

表单字段建议：

```text
目标岗位：Java 后端 / AI 应用开发 / 前端 / 算法 / 测开等
目标城市：北京 / 上海 / 杭州 / 深圳等
技术栈：Java、Spring Boot、Redis、MySQL、RAG、Agent...
项目经历：用户自己的项目描述
求职偏好：大厂、中厂、远程、日常实习、暑期实习等
排斥方向：测试、运维、实施、销售、外包等
实习要求：每周几天、几个月
补充说明：用户自由填写
```

#### 后端接口

```http
POST /api/profile/manual
GET  /api/profile/current
```

#### 完成标准

```text
1. 用户能在前端填写画像。
2. 后端能保存到 MySQL。
3. GET /api/profile/current 能查出当前画像。
4. 原来的 /api/job/analyze 不受影响。
```

---

### 阶段 2：AI 辅助生成结构化评分配置

目标：让 AI 根据用户画像生成一份结构化评分配置，而不是继续手写死规则。

#### 后端接口

```http
POST /api/profile/generate-scoring-config
```

#### 流程

```text
读取用户画像
  ↓
调用 DeepSeek
  ↓
要求模型只输出固定 JSON
  ↓
后端解析 JSON
  ↓
后端做字段校验、权重边界校验、关键词数量校验
  ↓
返回给前端预览
```

#### AI 生成配置示例

```json
{
  "targetRoles": ["Java后端", "AI应用开发", "大模型应用后端"],
  "preferredCities": ["北京"],
  "positiveKeywords": ["Java", "Spring Boot", "MySQL", "Redis", "RAG", "Agent", "Tool Calling"],
  "negativeKeywords": ["测试", "运维", "实施", "销售", "外包"],
  "hardRejectKeywords": ["电话销售", "纯测试", "驻场实施"],
  "scheduleRiskKeywords": ["6天/周", "7天/周"],
  "roleWeights": {
    "Java后端": 30,
    "AI应用开发": 25,
    "大模型应用": 20
  },
  "skillWeights": {
    "Java": 10,
    "Spring Boot": 10,
    "MySQL": 8,
    "Redis": 8,
    "RAG": 8,
    "Agent": 8,
    "Tool Calling": 8
  },
  "riskWeights": {
    "测试": -40,
    "运维": -35,
    "实施": -35,
    "销售": -50,
    "外包": -30
  }
}
```

#### 约束

AI 只能生成配置，不能生成可执行代码。

后端必须校验：

```text
1. JSON 是否可解析。
2. 必填字段是否存在。
3. 权重是否在合理范围，例如 -100 到 100。
4. keywords 数量是否过多，建议每类最多 50 个。
5. hardRejectKeywords 最多 30 个。
6. 不允许配置中包含 JS 代码、正则执行代码或危险字符串。
7. 校验失败时使用默认配置兜底。
```

#### 完成标准

```text
1. 用户提交画像后，可以生成一份评分配置。
2. 配置是固定 JSON 格式。
3. 后端能校验 AI 输出。
4. AI 输出失败时有默认配置兜底。
```

---

### 阶段 3：用户确认评分配置

目标：AI 生成的评分配置不能直接生效，必须由用户确认。

#### 前端展示

前端展示 AI 生成结果：

```text
目标岗位
目标城市
加分关键词
扣分关键词
硬性排除项
出勤风险项
角色权重
技能权重
风险权重
```

用户操作：

```text
确认使用
重新生成
手动修改
```

#### 后端接口

```http
POST /api/profile/scoring-config/confirm
GET  /api/profile/scoring-config
```

#### 完成标准

```text
1. AI 生成配置后不会直接生效。
2. 用户确认后才保存为正式配置。
3. userscript 可以通过 GET /api/profile/scoring-config 获取配置。
```

---

### 阶段 4：前端普通规则评分读取配置

目标：前端普通评分不再完全依赖写死的 Java 后端求职标准，而是读取当前用户的 scoring config。

#### userscript 启动流程

```text
页面加载
  ↓
GET /api/profile/scoring-config
  ↓
成功：使用用户配置评分
失败：使用默认配置兜底
```

#### 普通评分流程

```text
BOSS 岗位 JD
  ↓
userscript 提取岗位标题、公司、城市、薪资、出勤、JD 文本
  ↓
使用 scoring config 匹配关键词和权重
  ↓
计算规则分数
  ↓
输出：优先投 / 可投 / 谨慎投 / 不投
```

#### 重要原则

```text
普通评分不调 DeepSeek。
普通评分不走 RAG。
普通评分必须保持快、稳定、可解释。
```

#### 完成标准

```text
1. 前端普通评分能使用后端配置。
2. 换一份用户配置，评分倾向会变化。
3. 后端挂了时前端仍能用默认配置评分。
```

---

### 阶段 5：非结构化用户画像进入 RAG-Lite

目标：把用户画像里的长文本切成 chunk，用于 AI 深度核验时检索。

#### 数据来源

```text
用户手动填写的画像
项目经历详情
简历文本，后续可加 PDF 上传
求职偏好说明
历史投递反馈
后续面试复盘
```

#### 表设计建议

##### user_profile_document

```sql
CREATE TABLE IF NOT EXISTS user_profile_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  doc_type VARCHAR(50) NOT NULL,
  doc_name VARCHAR(255),
  source_type VARCHAR(50),
  raw_text MEDIUMTEXT,
  content_hash VARCHAR(128),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_profile_document_user_id (user_id),
  INDEX idx_user_profile_document_doc_type (doc_type)
);
```

`doc_type` 示例：

```text
manual_profile
resume
project
preference
feedback
interview_review
```

`source_type` 示例：

```text
manual_input
pdf_upload
system_generated
feedback
```

##### user_profile_chunk

```sql
CREATE TABLE IF NOT EXISTS user_profile_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  document_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  title VARCHAR(255),
  content TEXT NOT NULL,
  source_type VARCHAR(50),
  content_hash VARCHAR(128),
  embedding_json MEDIUMTEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_profile_chunk_user_id (user_id),
  INDEX idx_user_profile_chunk_document_id (document_id)
);
```

第一版 `embedding_json` 可以先为空，先做关键词检索。

#### 后端接口

```http
POST /api/profile/reindex
GET  /api/profile/search?query=Java Redis RAG&topK=5
```

#### 检索方式第一版

```text
读取 query
  ↓
按空格、标点、常见技术词做简单关键词拆分
  ↓
扫描 user_profile_chunk.content
  ↓
命中关键词越多分越高
  ↓
返回 topK
```

第一版不要做：

```text
embedding
Redis Vector
Milvus
Rerank
复杂多路召回
```

#### 完成标准

```text
1. /api/profile/reindex 能生成 chunk。
2. /api/profile/search 能搜出相关画像片段。
3. 搜 Redis / Java / RAG 时能返回对应项目经历。
```

---

### 阶段 6：AI 深度核验接入 RAG

目标：点击 AI 深度核验时，不再使用写死个人背景，而是动态检索用户画像。

#### 原流程

```text
POST /api/job/analyze
  ↓
Redis 查缓存
  ↓
未命中调用 DeepSeek
  ↓
保存 MySQL
  ↓
写 Redis
```

#### 新流程

```text
POST /api/job/analyze
  ↓
Redis 查缓存
  ↓
命中：直接返回
  ↓
未命中：
    1. 用 jobTitle + jobText 构造 query
    2. 检索 user_profile_chunk topK
    3. 把检索结果拼进 Prompt
    4. 调 DeepSeek
    5. 保存 job_record / job_analysis
    6. 写 Redis
    7. 返回 AI 分析
```

#### Prompt 结构

```text
你是一个岗位筛选助手。请根据当前岗位信息、规则评分结果、用户画像检索资料，判断该岗位是否值得投递。

【当前岗位信息】
岗位标题：{jobTitle}
公司名称：{companyName}
城市：{city}
薪资：{salary}
出勤：{schedule}
岗位描述：
{jobText}

【规则评分结果】
规则分数：{ruleScore}
规则结论：{ruleConclusion}

【用户画像检索结果】
资料1：{chunk1}
资料2：{chunk2}
资料3：{chunk3}

请输出严格 JSON：
{
  "decision": "优先投 / 可投 / 谨慎投 / 不投",
  "score": 0-100,
  "direction": "岗位方向判断",
  "reasons": ["理由1", "理由2"],
  "risks": ["风险1", "风险2"],
  "resumeMatches": ["简历匹配点1", "简历匹配点2"],
  "interviewFocus": ["面试重点1", "面试重点2"],
  "suggestedMessage": "建议开场白"
}
```

#### Redis 缓存 key 注意

建议把 profile 版本纳入缓存 key：

```text
ai-job-agent:analysis:{profileVersion}:{jobHash}
```

否则用户修改画像后，相同岗位可能还会命中旧分析结果。

`profileVersion` 可以先用：

```text
user_profile.updated_at 的 hash
```

或：

```text
scoring_config.updated_at + profile_document.updated_at 的 hash
```

#### 完成标准

```text
1. AI 深度核验能拿到 RAG 检索结果。
2. Prompt 里不再写死个人背景。
3. AI 返回能体现用户画像中的项目和技能。
4. 原有 Redis 缓存、MySQL 落库不坏。
```

---

### 阶段 5 最新收口状态

当前 `feature/ai-job-screening-agent` 分支已经完成 Profile RAG-Lite 在 AI 深度核验链路中的闭环接入。

#### 第 5.1：RAG-Lite 接入 `/api/job/analyze`

```text
POST /api/job/analyze
  ↓
Redis 查缓存
  ↓
命中：直接返回缓存结果，不重复检索用户画像
  ↓
未命中：
    1. 使用 jobTitle + city + schedule + duration + ruleScore + ruleConclusion + jobText 构造 profileQuery
    2. 调用 UserProfileRagService 检索 user_profile_chunk topK，默认 topK=5
    3. 将命中的用户画像 chunk 拼入 DeepSeek Prompt
    4. 调用 DeepSeek 生成 AI 深度核验结果
    5. 保存 job_record / job_analysis
    6. 将完整 response 写入 Redis
```

Redis cache key 已纳入 `profileVersion`：

```text
ai-job-agent:analysis:{profileVersion}:{jobHash}
```

`profileVersion` 优先使用最新 `user_profile_document.content_hash`，用于避免用户画像 reindex 后继续命中旧分析结果。

#### 第 5.2：`/api/job/analyze` 返回 RAG 命中证据

`/api/job/analyze` response 已新增可选字段 `profileRag`，用于观察本次分析使用到的用户画像资料：

```json
{
  "enabled": true,
  "profileVersion": "...",
  "query": "...",
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

该字段只用于调试和前端展示，不改变原有 `jobRecordId / taskId / status / decision / score / direction / reasons / risks / resumeMatches / interviewFocus / suggestedMessage` 字段含义。

#### 第 5.3：userscript 展示画像命中证据

userscript 的 AI 深度核验结果区域已经新增「画像命中证据」模块：

```text
是否启用：已启用 / 未启用
命中数量：chunkCount
画像版本：profileVersion 前 8 位
检索 Query：前 80 字
命中 chunks：最多 5 条，每条展示 title、score、sourceType、content 摘要
```

如果后端旧缓存或旧版本 response 没有 `profileRag` 字段，前端会保持原 AI 分析展示，不报错、不影响投递反馈保存。

#### 当前仍未实现的边界

```text
PDF 上传
embedding
Redis Vector
Milvus
Rerank
多用户登录
自动投递
读取 BOSS Cookie / Token
访问 BOSS 非公开接口
```

---

### 阶段 6：历史查询 + 反馈反哺 RAG + 当前岗位历史提醒

第 6 阶段目标是把已经沉淀在 `job_record / job_analysis / job_feedback` 中的历史岗位分析和投递反馈重新组织起来，形成闭环：

```text
历史岗位分析 / 投递反馈
  ↓
只读历史查询接口
  ↓
可选进入 Profile RAG-Lite
  ↓
AI 深度核验可参考历史偏好和反馈
  ↓
userscript 当前岗位面板提示历史记录
```

#### 第 6.1：历史岗位查询接口

新增只读接口：

```http
GET /api/jobs/recent?limit=20
GET /api/jobs/{jobRecordId}
GET /api/jobs/search?keyword=大模型&limit=20
GET /api/jobs/match?companyName=xxx&jobTitle=xxx
```

接口基于现有 `job_record / job_analysis / job_feedback` 表，不改变原有分析和反馈保存流程。返回字段包括岗位基本信息、AI 分析结论、解析后的 reasons / risks / resumeMatches / interviewFocus，以及最近一次投递反馈状态。

#### 第 6.2：历史分析 / 投递反馈进入 RAG-Lite

`POST /api/profile/reindex` 默认行为保持不变，只重建用户画像 chunk。

新增可选参数：

```http
POST /api/profile/reindex?includeHistory=true
```

当 `includeHistory=true` 时，会在原有用户画像 chunk 基础上追加最近 50 条历史记录生成的 chunk：

```text
标题：历史投递反馈 - {companyName} - {jobTitle}
内容：
公司、岗位、城市、薪资、出勤周期
AI 判断、AI 分数、方向
投递状态、沟通状态、面试状态
用户备注、主要风险、简历匹配点
sourceType：job_history 或 feedback_history
```

reindex 仍按 `profileName` 幂等重建，不会无限追加 document/chunk。

#### 第 6.3：userscript 展示当前岗位历史记录

userscript 当前识别出公司名和岗位名后，会调用：

```http
GET http://localhost:8080/api/jobs/match?companyName=...&jobTitle=...
```

如果有历史记录，会在右侧岗位匹配度面板中展示「历史记录」模块，最多显示最近 3 条：

```text
最近分析时间
AI 判断 / AI 分数
投递状态 / 沟通状态 / 面试状态
备注摘要
jobRecordId
```

后端不可用或无历史记录时静默处理，不影响评分面板、AI 深度核验、画像命中证据、投递反馈保存和聊天导出。

#### 第 6 阶段仍未实现的边界

```text
PDF 上传
embedding
Redis Vector
Milvus
Rerank
多用户登录
自动投递
自动发消息
读取 BOSS Cookie / Token
访问 BOSS 非公开接口
```

---

## 4. 建议数据表

### user_profile

```sql
CREATE TABLE IF NOT EXISTS user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  profile_name VARCHAR(100) NOT NULL DEFAULT 'default',
  target_roles TEXT,
  preferred_cities TEXT,
  skills TEXT,
  projects MEDIUMTEXT,
  preferences MEDIUMTEXT,
  reject_directions TEXT,
  internship_requirements TEXT,
  manual_text MEDIUMTEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### user_scoring_config

```sql
CREATE TABLE IF NOT EXISTS user_scoring_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 1,
  profile_id BIGINT,
  status VARCHAR(50) NOT NULL DEFAULT 'draft',
  config_json MEDIUMTEXT NOT NULL,
  generated_by VARCHAR(50),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_scoring_config_user_id (user_id),
  INDEX idx_user_scoring_config_status (status)
);
```

`status` 建议：

```text
draft
confirmed
archived
```

### user_profile_document

见阶段 5。

### user_profile_chunk

见阶段 5。

---

## 5. 建议后端包结构

```text
backend/src/main/java/com/lt/aijobscreeningagent/profile/
  controller/
    UserProfileController.java
  service/
    UserProfileService.java
    ScoringConfigGenerationService.java
    UserProfileRagService.java
  model/
    UserProfile.java
    UserScoringConfig.java
    UserProfileDocument.java
    UserProfileChunk.java
  dto/
    ManualProfileRequest.java
    UserProfileResponse.java
    ScoringConfigResponse.java
    ConfirmScoringConfigRequest.java
    ProfileSearchResponse.java
  mapper/ 或 repository/
    UserProfileMapper.java
    UserScoringConfigMapper.java
    UserProfileDocumentMapper.java
    UserProfileChunkMapper.java
```

具体命名应尽量和当前项目已有风格保持一致。

---

## 6. LLM 生成 scoring config 的 Prompt 建议

```text
你是一个岗位筛选规则配置生成器。
你的任务是根据用户的求职画像，生成一份结构化岗位评分配置。
这份配置会被前端脚本用于本地快速规则评分，所以必须稳定、可解释、字段固定。

要求：
1. 只输出 JSON，不要输出 Markdown，不要输出解释文字。
2. 不要生成 JavaScript 代码。
3. 不要生成正则表达式执行代码。
4. 权重必须在 -100 到 100 之间。
5. positiveKeywords、negativeKeywords、hardRejectKeywords 不要过多。
6. hardRejectKeywords 应只放强排除项。
7. 如果用户信息不足，请生成保守配置。

用户画像：
{profileText}

请输出如下 JSON 格式：
{
  "targetRoles": [],
  "preferredCities": [],
  "positiveKeywords": [],
  "negativeKeywords": [],
  "hardRejectKeywords": [],
  "scheduleRiskKeywords": [],
  "roleWeights": {},
  "skillWeights": {},
  "riskWeights": {}
}
```

---

## 7. 前端 userscript 改造要求

### 当前目标

不要大改 UI，只加必要入口和配置拉取。

### 新增能力

```text
1. 初始化个人画像按钮。
2. 画像表单弹窗 / 面板。
3. 保存画像到 POST /api/profile/manual。
4. 生成评分配置按钮，调用 POST /api/profile/generate-scoring-config。
5. 展示评分配置预览。
6. 确认配置，调用 POST /api/profile/scoring-config/confirm。
7. 页面加载时 GET /api/profile/scoring-config。
8. 普通评分使用配置中的关键词和权重。
9. 后端不可用时使用默认配置兜底。
```

### 不要做

```text
不要一次性做复杂后台管理页。
不要做 PDF 上传。
不要让前端执行 AI 返回的代码。
不要破坏现有导出聊天列表、规则评分、AI 深度核验、反馈保存功能。
```

---

## 8. Codex 实现顺序

建议按 commit 拆：

```text
commit 1: add user profile table and manual profile api
commit 2: add scoring config generation api with DeepSeek
commit 3: add scoring config confirm/query api
commit 4: update userscript to load scoring config
commit 5: add profile document/chunk tables and reindex/search api
commit 6: integrate profile RAG context into job analyze prompt
commit 7: add docs for user profile scoring config and RAG-Lite
```

每个 commit 必须保证：

```text
后端能启动
原有接口不坏
userscript 原有按钮不消失
```

---

## 9. 验收测试

### 测试 1：保存用户画像

```http
POST /api/profile/manual
```

输入：

```json
{
  "targetRoles": "Java后端, AI应用开发",
  "preferredCities": "北京",
  "skills": "Java, Spring Boot, MySQL, Redis, RAG, Agent",
  "projects": "黑马点评项目；AI Job Screening Agent 项目",
  "preferences": "优先 Java 后端和大模型应用后端",
  "rejectDirections": "测试, 运维, 实施, 销售, 外包",
  "internshipRequirements": "每周至少 4 天，3 个月以上",
  "manualText": "不考虑纯算法训练，更偏 Java 后端和 AI 应用工程。"
}
```

期望：

```text
MySQL user_profile 有记录。
GET /api/profile/current 能查到。
```

### 测试 2：AI 生成评分配置

```http
POST /api/profile/generate-scoring-config
```

期望：

```text
返回 targetRoles、positiveKeywords、negativeKeywords、hardRejectKeywords、weights。
JSON 可解析。
权重在合理范围。
```

### 测试 3：确认配置

```http
POST /api/profile/scoring-config/confirm
GET  /api/profile/scoring-config
```

期望：

```text
GET 能返回 confirmed 配置。
```

### 测试 4：前端普通评分使用配置

换一份配置：

```text
目标岗位：前端开发
positiveKeywords：React、Vue、TypeScript
negativeKeywords：Java、后端
```

期望：

```text
同一个 Java 后端岗位不再高分。
前端岗位更高分。
```

### 测试 5：RAG reindex/search

```http
POST /api/profile/reindex
GET /api/profile/search?query=Java Redis RAG&topK=5
```

期望：

```text
能返回包含 Java / Redis / RAG 的用户画像 chunk。
```

### 测试 6：AI 深度核验接入 RAG

```http
POST /api/job/analyze
```

岗位 JD 包含：

```text
Java 后端、Spring Boot、MySQL、Redis、RAG、Agent
```

期望：

```text
AI 结果里的 resumeMatches 能体现用户画像中的 Java、Redis、RAG、Agent 项目。
```

---

## 10. 非目标

本阶段不做：

```text
多用户登录系统
PDF 简历上传
复杂 PDF 解析
embedding
Redis Vector
Milvus
Rerank
自动投递
自动发送消息
读取 BOSS Cookie / Token
访问 BOSS 非公开接口
绕过验证码
```

---

## 11. 面试讲法

可以这样讲：

```text
项目最开始为了快速验证，我把用户背景和求职目标写死在 Prompt 和前端规则里，比如默认用户是 Java 后端 / AI 应用开发方向。但这会导致系统只能适配单个用户，用户背景变化或者换一个用户时都要改代码。

所以我设计了用户画像初始化模块。用户首次使用时可以填写目标岗位、城市、技术栈、项目经历和排斥方向。后端会调用大模型，把自然语言用户画像转换成结构化评分配置，例如目标岗位、加分关键词、扣分关键词、硬性排除项和权重。用户确认后，前端脚本拉取这份配置，在本地完成快速规则评分。

同时，用户的非结构化资料会被切分成 RAG chunk。AI 深度核验时，系统会根据岗位 JD 检索最相关的用户画像片段，再拼进通用 Prompt 调用 DeepSeek。这样普通评分保持低延迟和可解释，深度分析又能结合用户真实简历和项目资料，实现从写死 Prompt 到可适配多用户画像的岗位筛选 Agent 升级。
```

---

## 12. 项目含金量表达

这个方案的含金量不在于“RAG 技术多复杂”，而在于工程取舍清楚：

```text
1. 普通评分使用结构化配置，保证快、稳定、可解释。
2. AI 只在初始化阶段生成配置，避免每个岗位都调模型。
3. RAG 只在深度核验阶段检索资料，保证个性化和可维护性。
4. Redis 缓存避免重复调用 DeepSeek。
5. MySQL 沉淀岗位、分析结果、用户画像和反馈数据。
6. 投递反馈后续可以反哺用户画像和评分规则。
```

最终项目定位：

```text
一个用户画像驱动的 AI Job Screening Agent：
通过用户初始化画像、AI 生成结构化评分配置、RAG 检索用户资料、规则评分与大模型深度核验结合，帮助不同用户在 BOSS 岗位页面上快速判断岗位是否值得投递。
```
