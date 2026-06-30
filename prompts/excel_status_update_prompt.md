# Excel Status Update Prompt

English purpose: Convert chat analysis or follow-up notes into structured Excel tracker updates.

用途：用于把聊天分析或跟进记录转换成 Excel 跟进表的字段更新。

You are helping me update an existing job application tracker based on chat status.

## Tracker Fields To Update

- Current Status
- Read Time
- Reply Time
- Interview Time
- Interview Round
- Interview Result
- Blocker or Rejection Reason
- Next Action
- Notes

## Update Rules

- `P0`: set `Current Status` to `Replied` or `Interview Scheduled` depending on the message. Set `Next Action` to `Send Resume`, `Prepare Interview`, or another suitable action.
- `P1`: set `Current Status` to `Read No Reply`. Set `Next Action` to `Follow Up`.
- `P2`: keep waiting. Usually set `Next Action` to `Wait for Reply`.
- `P9`: set `Current Status` to `Rejected` when the message clearly indicates mismatch or rejection. Set `Next Action` to `Give Up`.

## Matching Rules

- Match by company first.
- If position is available, match by company + position.
- If there are multiple possible matches, flag them instead of guessing.

## Please Output

1. Rows to update
2. Exact field changes
3. Any uncertain matches
4. A short update summary

## Input

```text
[PASTE CHAT ANALYSIS OR TSV HERE]
```
