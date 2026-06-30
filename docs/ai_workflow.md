# AI Workflow

English Summary:
This document explains how to use AI prompts for job screening, chat status analysis, tracker updates, daily review, and interview review.

中文摘要：
本文说明如何使用 AI 提示词完成岗位筛选、聊天状态分析、表格更新、每日复盘和面试复盘。

AI is used as an assistant for summarization, prioritization, and wording. It is not the source of truth.

## Recommended Inputs

- TSV exported by the userscript
- selected rows from the Excel tracker
- job descriptions
- interview notes

## Recommended Outputs

- priority grouping
- tracker update suggestions
- polite follow-up messages
- daily review summaries
- interview review summaries

## Typical Chat Status Analysis

1. Paste `examples/mock_chat_export.tsv` style data.
2. Ask AI to classify P0/P1/P2/P9 items.
3. Ask AI to suggest tracker updates.
4. Manually verify before updating Excel.

## Privacy Reminder

Before sharing data with an AI tool, remove real names, real contact information, private company messages, and screenshots if they are not necessary.
