# Privacy & Compliance（隐私与合规）

`ai-job-screening-agent` 是个人求职辅助工具，设计边界是 local-first 和 compliance-aware。

## What It Does（项目做什么）

- 读取用户当前主动打开页面中的可见 DOM 文本。
- 在本地浏览器中做 rule-based scoring。
- 用户手动触发后，将岗位字段发送到本地 Spring Boot 后端。
- 后端保存岗位记录、分析结果和用户主动填写的反馈。
- 后端可调用 DeepSeek API 生成岗位分析。

## What It Does Not Do（项目不做什么）

- 不读取 Cookie / Token。
- 不访问招聘平台非公开 API。
- 不自动投递。
- 不自动发送消息。
- 不绕过验证码或反爬机制。
- 不做批量爬取。
- 不采集非当前用户主动查看的信息。
- 不把项目描述成生产级招聘自动化系统。

## Sensitive Information（敏感信息）

不要提交以下内容：

- real `DEEPSEEK_API_KEY`
- real MySQL password
- real Redis password
- Cookie
- Token
- 真实聊天记录或个人隐私数据

仓库中只应保留 `.env.example` 这类占位配置。真实值应放在本机环境变量或未提交的 `.env` 文件中。

## Data Boundary（数据边界）

MySQL 保存的是用户主动分析过的岗位记录、AI 分析结果、用户画像和反馈。Redis 保存的是岗位分析 response 缓存。项目没有实现平台级批量数据采集，也不应被用于绕过招聘平台规则。
