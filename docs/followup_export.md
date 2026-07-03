# Optional Follow-up Export（可选沟通状态导出）

`userscripts/boss-chat-followup-export.user.js` 是 AI Job Screening Agent 的可选辅助模块，脚本名称为 `BOSS Follow-up Status Export`，namespace 为 `ai-job-screening-agent-followup`。

它用于整理用户投递后的沟通状态，保留沟通状态扫描、解析、TSV 导出、复制到剪贴板和导出结果面板能力，但不再放在主岗位筛选脚本中，避免和 AI Job Screening Agent 的主项目定位混在一起。

## Purpose（用途）

- 导出当前页面可见的沟通状态信息。
- 辅助用户整理投递跟进记录。
- 为后续人工填写 feedback 或整理历史记录提供参考。

它不是岗位筛选 Agent 的核心链路，不调用 `/api/job/analyze`，也不参与 DeepSeek analysis、Redis cache、Profile RAG-Lite 或 MySQL persistence 的主分析流程。

## Usage（使用方式）

1. 在 Tampermonkey 或兼容 Userscript 管理器中安装 `userscripts/boss-chat-followup-export.user.js`。
2. 打开用户主动查看的招聘沟通页面。
3. 点击页面上的 `导出沟通状态` 按钮。
4. 查看 `沟通状态导出结果 / Follow-up Status Export` 面板，并按需复制整理。

## Boundary（边界）

- 只读取当前页面可见 DOM。
- 不读取 Cookie / Token。
- 不访问招聘平台非公开 API。
- 不自动投递。
- 不自动发送消息。
- 不绕过验证码或反爬机制。
- 不做批量爬取。

## Relationship to Main Agent（与主 Agent 的关系）

主脚本 `userscripts/boss-job-screening-agent.user.js` 聚焦岗位筛选：岗位信息提取、rule-based screening、scoring config 加载、AI 深度核验按钮、`POST http://localhost:8080/api/job/analyze` 调用、AI 分析结果展示、历史记录展示和投递反馈保存。

可选导出脚本只负责沟通状态整理。它可以辅助反馈闭环，但不是主 Agent 分析链路的一部分。
