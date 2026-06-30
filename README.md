# AI Job Application Tracker Toolkit

A local-first toolkit for personal job application follow-up, combining an Excel tracker, a recruitment chat status export userscript, and ready-to-use AI prompts.

This project is designed for job seekers who want to organize application status, chat follow-up, interview notes, and daily review workflows without relying on screenshots or scattered notes.

## Why This Project

When job applications increase, it becomes easy to lose track of which companies have been contacted, which messages were read, which messages were delivered, which recruiters replied, and which opportunities already ended.

This toolkit is designed to solve several practical problems:

- application status becomes messy after many submissions
- read, delivered, replied, and rejected states are hard to track manually
- taking screenshots for AI analysis is inconvenient
- interview review notes are often unstructured
- application count alone does not show reply rate, interview rate, or rejection reasons

## What This Project Includes

- Excel job application tracker template
- Dashboard for application, reply, interview, and offer metrics
- Tampermonkey userscript for exporting visible recruitment chat status into TSV
- AI prompts for job screening, chat status analysis, Excel updates, daily review, interview review, and HR replies
- Mock examples for GitHub demonstration
- Documentation for workflow, status rules, Excel design, and compliance boundary

## Local-First Positioning

This toolkit is built for personal local information organization.

中文定位：

- AI 辅助求职跟进工具包
- Excel 求职跟进表
- 招聘聊天状态导出 userscript
- 可复制使用的 AI prompts
- 本地优先 local-first
- 不抓接口
- 不绕验证码
- 不自动投递
- 不自动发送消息
- 示例全部使用 mock 数据

It does not:

- request private platform APIs
- bypass verification checks
- auto-apply to jobs
- auto-send messages
- access data that is not already visible to the logged-in user
- include real HR names, real company communications, or real screenshots in examples

The userscript only reads text already displayed on the current browser page and turns it into TSV for personal follow-up.

## Workflow

```text
Job Screening → Excel Tracker → Chat Status Export → AI Analysis → Tracker Update → Daily Review → Interview Review
```

1. Use AI prompts to screen job descriptions before adding them to the tracker.
2. Record selected jobs in the Excel tracker.
3. Export visible recruitment chat status into TSV when follow-up review is needed.
4. Ask AI to group messages by action priority.
5. Update the tracker with read, reply, interview, rejection, and next-action status.
6. Summarize the day in the daily review sheet.
7. Record interview questions and weak spots in the interview review sheet.

## What This Project Is Not

This project does not scrape private APIs, bypass verification, automate job applications, or send messages automatically.

本项目不抓取非公开接口，不绕过验证码，不自动投递，不自动发送消息，不批量采集招聘数据。它只用于个人求职过程中的本地记录、页面可见信息整理和 AI 辅助分析。

## Repository Structure

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

## Quick Start

1. Open `templates/job_tracker_template.xlsx`.
2. Fill applications in the `Applications` sheet.
3. Use `userscripts/job-chat-status-export.user.js` in Tampermonkey to export visible chat status as TSV.
4. Paste the TSV into an AI tool with `prompts/chat_status_analysis_prompt.md`.
5. Update the Excel tracker using the AI output.
6. Review progress in the `Dashboard`, `Daily Review`, and `Interview Review` sheets.

## Excel Tracker

The template contains:

- `Applications`: main job application tracking table
- `Dashboard`: formula-based metrics
- `Daily Review`: daily review log
- `Interview Review`: interview notes and follow-up review
- `Config`: dropdown options for consistent status management

The template is empty and contains no real application data.

## Userscript

The userscript exports visible chat list text into TSV with these fields:

```text
联系人	公司	身份	时间	状态	行动等级	下一步	建议	最后消息	原始文本
```

The action levels are:

- `P0`: needs immediate attention
- `P1`: read but not replied, can follow up later
- `P2`: delivered but unread, wait first
- `P9`: clear rejection or low priority

## AI Prompts

The `prompts/` directory contains copy-ready prompts for:

- screening jobs before adding them to the tracker
- analyzing exported chat status
- appending new rows to Excel
- updating existing rows
- daily job search review
- interview review
- drafting polite HR replies

## Examples

All files under `examples/` use mock data only. Do not upload real chat records, real HR names, real company communication, real contact information, or real screenshots.

## License

MIT License. See [LICENSE](LICENSE).
