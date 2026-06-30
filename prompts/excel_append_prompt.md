# Excel Append Prompt

English purpose: Convert job notes into rows that can be appended to the Excel tracker.

用途：用于把岗位信息整理成可以追加到 Excel 跟进表的新行。

You are helping me append new job applications to my Excel tracker.

Please convert the following job information into rows matching the `Applications` sheet fields.

## Applications Sheet Fields

`ID, Application Date, Platform, Company, Position, City, Company Size, Salary Range, Job Link, Priority Tier, Job Type, JD Keywords, Match Level, Contacted Proactively, Opening Message Version, Resume Version, Current Status, Read Time, Reply Time, Interview Time, Interview Round, Interview Result, Blocker or Rejection Reason, Next Action, Notes`

## Rules

- Use `Contacted` as the default status if I have already sent a message.
- Use `To Contact` if the job is only recorded and no message has been sent.
- Leave unknown fields blank.
- Keep JD keywords short and useful.
- Do not invent company details.
- Output as a Markdown table first, then as CSV.

## Input

```text
[PASTE JOBS OR NOTES HERE]
```
