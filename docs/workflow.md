# Workflow

English Summary:
This document explains the local-first workflow from job screening to Excel tracking, visible chat status export, AI analysis, tracker updates, daily review, and interview review.

中文摘要：
本文说明从岗位筛选、Excel 跟进、页面可见聊天状态导出、AI 分析、表格更新、每日复盘到面试复盘的完整流程。

This toolkit follows a local-first job application workflow.

## 1. Screen Jobs

Use `prompts/job_screening_prompt.md` to evaluate whether a job is worth adding to the tracker.

The goal is to classify jobs into:

- `A - High Match`
- `B - Worth Applying`
- `C - Practice`
- `Skip`

## 2. Add Jobs To Excel

Record selected jobs in `templates/job_tracker_template.xlsx`.

Use the `Applications` sheet for the main list and keep all status values consistent through dropdowns.

## 3. Export Visible Chat Status

Install `userscripts/job-chat-status-export.user.js` with Tampermonkey.

Open the recruitment chat page and click `导出聊天列表`. The script exports visible chat list text into TSV.

## 4. Analyze With AI

Paste the TSV into an AI tool with `prompts/chat_status_analysis_prompt.md`.

The AI should group items by:

- `P0`: immediate action
- `P1`: follow up later
- `P2`: wait
- `P9`: no action

## 5. Update Tracker

Use `prompts/excel_status_update_prompt.md` to convert the AI analysis into Excel field updates.

Update `Current Status`, `Read Time`, `Reply Time`, `Next Action`, and `Notes`.

## 6. Review Daily

Use `Daily Review` to summarize progress, exposed problems, and tomorrow's plan.

## 7. Review Interviews

Use `Interview Review` to track interview questions, weak spots, and next preparation tasks.
