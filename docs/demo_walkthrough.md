# Demo Walkthrough（演示流程）

本文档用于准备面试演示，展示 `ai-job-screening-agent` 的核心闭环：岗位页面提取、规则评分、AI 分析、RAG-Lite 命中证据、历史记录和反馈保存。

## Prepare Dependencies（准备依赖）

```bash
docker compose up -d
```

初始化数据库：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -proot ai_job_agent < backend/src/main/resources/schema.sql
```

配置环境变量：

```bash
cp .env.example .env
```

在本机填入 `DEEPSEEK_API_KEY`，不要把真实值提交到 GitHub。

## Start Backend（启动后端）

```bash
cd backend
mvn spring-boot:run
```

检查服务：

```bash
curl http://localhost:8080/api/health
```

## Prepare Profile（准备用户画像）

通过 `POST /api/profile/manual` 保存默认画像，然后执行：

```bash
curl -X POST "http://localhost:8080/api/profile/reindex?includeHistory=false"
```

首次演示可以只使用手动画像；已有投递反馈后，再使用 `includeHistory=true` 将历史记录纳入 RAG-Lite。

## Install Userscript（安装脚本）

将 `userscripts/boss-job-screening-agent.user.js` 安装到 Tampermonkey 或兼容 Userscript 管理器。

打开支持的 BOSS 岗位页面后，页面右下角会展示岗位评分面板。AI 分析需要用户手动点击触发。

## Recommended Demo Path（推荐演示路径）

1. 打开一个岗位详情页，展示 Userscript 从可见 DOM 提取岗位字段。
2. 展示 rule-based scoring 的本地评分结果、命中关键词和风险点。
3. 点击 AI 分析按钮，后端执行 Redis cache 检查、Profile RAG-Lite 检索和 DeepSeek 分析。
4. 展示返回结果中的 `decision`、`score`、`reasons`、`risks`、`resumeMatches`、`interviewFocus` 和 `profileRag`。
5. 保存一次投递反馈。
6. 查询 `GET /api/jobs/recent` 或 `GET /api/job/feedback?jobRecordId=...` 展示历史记录。

## Screenshots（截图）

当前仓库还没有截图或 demo.gif。建议后续补充岗位评分面板、AI 分析结果、历史记录或 profileRag 命中证据截图。

## Compliance Notes（合规说明）

演示时建议主动说明：项目只读取当前页面可见 DOM，不读取 Cookie / Token，不访问非公开 API，不自动投递，不自动发送消息，不绕过验证码或反爬机制。
