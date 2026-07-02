# GitHub Release Checklist

这份清单用于项目公开发布前的最后检查，重点是避免泄露真实隐私、误提交临时文件，以及避免文档夸大当前能力。

## 1. 提交前检查

- 确认当前分支正确，例如 `feature/ai-job-screening-agent`。
- 执行 `git status --short`，确认变更文件符合预期。
- 确认没有误改 `backend/src/`、`userscripts/`、`schema.sql`、`pom.xml`，除非当前任务明确要求。
- 文档提交前通读 README 和 docs 的新增部分。
- 示例数据必须是 mock 数据。

## 2. API Key / 密码检查

检查以下内容不能出现在仓库中：

- 真实 `DEEPSEEK_API_KEY`。
- MySQL 真实密码。
- Redis 真实密码。
- Cookie。
- Token。
- 私人手机号、邮箱、聊天记录、截图。

推荐命令：

```bash
git grep -n "DEEPSEEK_API_KEY"
git grep -n "MYSQL_PASSWORD"
git grep -n "REDIS_PASSWORD"
git grep -n "Cookie"
git grep -n "Token"
```

允许出现 `.env.example` 中的空值示例。

## 3. target、logs、临时文件检查

确认 `.gitignore` 已覆盖：

- `target/`
- `*.log`
- `.env`
- `*.tmp`
- `*.bak`
- `.DS_Store`
- `.idea/`
- `.vscode/`
- `node_modules/`
- `dist/`

检查命令：

```bash
git status --short
```

如果出现 `target/`、日志、临时压缩包或本地配置文件，不要提交。

## 4. README 检查

README 需要包含：

- 项目一句话定位。
- 核心功能列表。
- Mermaid 架构图。
- 快速启动：MySQL、Redis、DeepSeek API Key、Spring Boot、Tampermonkey。
- 合规边界。
- 当前限制。
- 后续规划。

README 不应包含：

- 真实密钥。
- 真实投递记录。
- 真实 HR 姓名或聊天内容。
- 把未完成能力写成已完成的描述。

## 5. docs 检查

重点检查：

- `docs/architecture.md`：架构是否与当前实现一致。
- `docs/api_reference.md`：接口路径是否和 Controller 一致。
- `docs/demo_walkthrough.md`：演示步骤是否能按顺序走通。
- `docs/resume_project_description.md`：简历描述是否准确。
- `docs/interview_guide.md`：是否适合面试背诵。
- `docs/user_profile_scoring_rag_lite_design.md`：阶段状态是否最新。

## 6. 演示截图检查

如果准备上传截图，必须确认：

- 不出现真实公司沟通内容。
- 不出现真实 HR 姓名。
- 不出现手机号、微信、邮箱。
- 不出现真实 Cookie / Token / API Key。
- BOSS 页面截图只展示必要区域，最好使用 mock 或打码数据。

当前仓库默认不提交截图文件。

## 7. 当前未完成能力声明检查

文档必须明确当前仍未完成：

- PDF 简历上传和解析。
- embedding。
- Redis Vector。
- Milvus。
- Rerank。
- 多用户登录。
- 自动投递。
- 自动发送消息。

不要把 RAG-Lite 写成向量数据库 RAG。

## 8. 合规边界检查

README 和 docs 中必须保留：

- 只读取当前页面可见 DOM 文本。
- 不读取 Cookie / Token。
- 不访问 BOSS 非公开接口。
- 不绕过验证码或登录校验。
- 不自动投递。
- 不自动发送消息。
- 最终是否投递由用户人工决定。

## 9. Git 提交流程

建议流程：

```bash
git status --short
git diff -- README.md docs .env.example .gitignore
git add README.md docs .env.example .gitignore
git commit -m "docs: polish github release materials"
git push
```

如果本次任务明确禁止修改业务代码，提交前再次确认：

```bash
git diff --name-only
```

输出中不应出现：

- `backend/src/`
- `userscripts/`
- `backend/src/main/resources/schema.sql`
- `backend/pom.xml`
