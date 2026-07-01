# AI Job Application Tracker Toolkit
> AI 辅助求职跟进工具包

This is a local-first personal job application tracking toolkit.

它是一个本地优先的个人求职跟进工具包，用于整理投递记录、聊天状态、AI 分析结果、每日复盘和面试复盘。

The toolkit has three core parts:

本项目由三部分组成：

- Excel tracker template / 求职跟进 Excel 模板
- Chat status export userscript / 聊天状态导出用户脚本
- AI prompt workflow / AI 分析提示词工作流

## Overview / 项目简介

AI Job Application Tracker Toolkit helps job seekers organize application progress, communication status, follow-up actions, and interview review notes in a structured local workflow.

AI 辅助求职跟进工具包面向个人求职场景，帮助你用 Excel、用户脚本和 AI 提示词，把岗位筛选、投递记录、聊天状态、后续动作和面试复盘串成一个清晰流程。

## Why This Project / 为什么做这个项目

When applications increase, status tracking can become messy. It is hard to remember which jobs were contacted, which messages were read, which opportunities received replies, and which ones already ended.

投递岗位变多以后，状态很容易混乱：哪些已读、哪些送达、哪些有回复、哪些已经拒绝，靠截图和手动记忆都不稳定。

This project helps with:

这个项目主要解决：

- application status becomes messy after many submissions / 投递多后状态混乱
- read, delivered, replied, and rejected states are hard to track / 已读、送达、回复、拒绝难跟踪
- screenshot-based AI analysis is inconvenient / 截图给 AI 分析比较麻烦
- interview review notes are often unstructured / 面试复盘不够结构化
- application count alone does not show reply rate, interview rate, or rejection reasons / 只看投递数量，看不到回复率、约面率和拒绝原因

## Features / 功能特性

- Excel job tracker with dropdowns, formulas, conditional formatting, and dashboard metrics  
  带下拉框、公式、条件格式和仪表盘的求职跟进 Excel 模板
- Visible chat status export userscript for TSV output  
  将页面已展示的聊天状态整理为 TSV 的用户脚本
- Real-time job fit scoring based on visible page text and local rules  
  基于页面可见文本和本地规则的岗位匹配度实时评分
- AI prompts for job screening, chat analysis, tracker updates, daily review, interview review, and HR replies  
  覆盖岗位筛选、聊天分析、表格更新、每日复盘、面试复盘和 HR 回复的 AI 提示词
- Mock examples for public GitHub demonstration  
  适合公开仓库展示的 mock 示例数据
- Local-first workflow for personal job-search management  
  本地优先的个人求职跟进流程

## Workflow / 工作流

```text
Job Screening → Excel Tracker → Chat Status Export → AI Analysis → Tracker Update → Daily Review → Interview Review
```

1. Screen jobs with AI prompts before adding them to the tracker.  
   使用 AI 提示词先筛选岗位，再决定是否加入表格。
2. Record selected jobs in the Excel tracker.  
   在 Excel 跟进表中记录公司、岗位、平台、状态和下一步动作。
3. Export visible recruitment chat status into TSV when needed.  
   需要复盘沟通状态时，将页面已展示的聊天状态导出为 TSV。
4. Analyze the TSV with AI.  
   使用 AI 按 P0/P1/P2/P9 分析优先级。
5. Update the tracker with verified results.  
   人工确认后更新 Excel 表格。
6. Summarize daily progress.  
   每天记录投递效果、问题和明日策略。
7. Review interviews in a structured way.  
   用结构化字段复盘面试问题、薄弱点和后续准备。

## What This Project Is Not / 项目边界

This project does not access non-public platform APIs, handle verification or login checks, perform job application actions, or perform message sending actions.

本项目仅用于个人求职过程中的本地记录和页面可见信息整理；不访问非公开接口，不处理验证码或登录校验，不执行投递或消息发送操作，也不做平台数据采集。

The userscript only reads text already displayed on the current browser page and exports it into TSV for personal follow-up.

用户脚本只读取当前浏览器页面中已经展示出来的文本，并将其整理为 TSV，方便个人求职跟进。

## Quick Start / 快速开始

1. Open `templates/job_tracker_template.xlsx`.  
   打开 `templates/job_tracker_template.xlsx`。
2. Fill applications in the `Applications` sheet.  
   在 `Applications` 表中填写投递记录。
3. Install `userscripts/job-chat-status-export.user.js` in Tampermonkey.  
   在 Tampermonkey 中安装 `userscripts/job-chat-status-export.user.js`。
4. Review the floating job fit scoring panel on job search or detail pages.  
   在职位搜索页或岗位详情页查看浮动的岗位匹配度评分面板。
5. Export visible chat status into TSV.  
   将页面已展示的聊天状态导出为 TSV。
6. Paste TSV into an AI tool with `prompts/chat_status_analysis_prompt.md`.  
   使用 `prompts/chat_status_analysis_prompt.md` 让 AI 分析 TSV。
7. Update the tracker and review the dashboard.  
   更新表格并查看仪表盘统计。

## Job Fit Scoring / 岗位匹配度实时评分

The userscript includes a local rule-based `Job Fit Scoring` panel. It reads visible text from the current browser page, scores the job from 0 to 100, and shows a suggested conclusion such as `优先投`, `可投`, `谨慎投`, or `不投`.

用户脚本包含一个本地规则驱动的「岗位匹配度实时评分」面板。它只读取当前浏览器页面上已经展示出来的岗位文本，并根据关键词规则给出 0-100 分、方向判断、Excel 档位、命中关键词、风险点和推荐开场白版本。

Important boundary:

边界说明：

- It is based only on visible page text and local rules.  
  本功能仅基于当前页面可见文本进行本地规则评分。
- It does not access non-public platform APIs.  
  不访问非公开接口。
- It does not bypass verification.  
  不绕过验证码。
- It does not perform job application actions.  
  不自动投递。
- It does not perform message sending actions.  
  不自动发送消息。
- The score is only a reference; the final decision is made by the user.  
  评分不保证完全准确，最终是否投递由用户人工决定。

## Directory Structure / 目录结构

```text
ai-job-application-tracker-toolkit/
├─ README.md
├─ LICENSE
├─ .gitignore
├─ userscripts/
│  └─ job-chat-status-export.user.js
├─ templates/
│  ├─ job_tracker_template.xlsx
│  ├─ job_tracker_fields.md
│  └─ status_options.md
├─ prompts/
│  ├─ job_screening_prompt.md
│  ├─ chat_status_analysis_prompt.md
│  ├─ excel_append_prompt.md
│  ├─ excel_status_update_prompt.md
│  ├─ daily_review_prompt.md
│  ├─ interview_review_prompt.md
│  └─ hr_reply_prompt.md
├─ docs/
│  ├─ workflow.md
│  ├─ excel_design.md
│  ├─ ai_workflow.md
│  ├─ status_rules.md
│  ├─ compliance_boundary.md
│  └─ resume_project_description.md
└─ examples/
   ├─ mock_tracker.xlsx
   ├─ mock_jobs.csv
   ├─ mock_chat_export.tsv
   ├─ mock_ai_input.md
   ├─ mock_ai_output.md
   └─ mock_codex_update_prompt.md
```

## Examples / 示例数据

All files under `examples/` use mock data only.

`examples/` 目录下只包含 mock 示例数据。

Included examples:

示例包括：

- `mock_tracker.xlsx`: mock Excel tracker / mock 求职跟进表
- `mock_jobs.csv`: mock job list / mock 岗位列表
- `mock_chat_export.tsv`: mock chat status export / mock 聊天状态导出
- `mock_ai_input.md`: mock AI input / mock AI 输入
- `mock_ai_output.md`: mock AI output / mock AI 输出
- `mock_codex_update_prompt.md`: mock update prompt / mock 更新提示词

## Privacy & Compliance / 隐私与合规

Do not upload real chat records, real HR names, real company communication, real contact information, or real screenshots.

请不要上传真实聊天记录、真实 HR 姓名、真实公司沟通内容、真实联系方式或真实截图。

The template workbook is empty. The example workbook uses mock data only.

模板工作簿为空模板；示例工作簿只使用 mock 数据。

## Resume Usage / 简历写法

Suggested resume description:

简历描述示例：

```text
Built a local-first AI-assisted job application tracking toolkit with an Excel tracker template, dashboard metrics, a visible chat status export userscript, reusable AI prompts, and mock examples for privacy-safe demonstration.
```

```text
设计并实现本地优先的 AI 辅助求职跟进工具包，包含 Excel 求职跟进模板、状态统计仪表盘、页面可见聊天状态导出用户脚本、AI 分析提示词和脱敏 mock 示例，用于个人求职过程中的状态整理、跟进管理和面试复盘。
```

## License / 许可证

MIT License. See [LICENSE](LICENSE).

本项目使用 MIT License，详见 [LICENSE](LICENSE)。
