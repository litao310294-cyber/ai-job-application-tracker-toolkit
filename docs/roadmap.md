# Roadmap（后续规划）

本 Roadmap 只列出与当前实现自然衔接的扩展方向，避免把项目描述成未实现的生产级系统。

## Near Term（近期）

- More structured feedback loop（更结构化反馈闭环）：把投递结果、拒绝原因、面试结果整理成更稳定的字段。
- Better profile management（更好的用户画像管理）：支持更清晰的默认画像编辑、版本说明和 reindex 提示。
- Basic dashboard or screenshots（基础看板或截图）：补充岗位评分面板、AI 分析结果、历史记录和 RAG-Lite 命中证据截图。

## Later（后续）

- Optional embedding-based retrieval（可选向量检索）：当用户画像、历史反馈和简历材料变多后，再考虑 Embedding + Vector Store + Rerank。
- More detailed tool call logging（更细粒度工具调用日志）：扩展 `tool_call_log` 表记录工具名、输入摘要、输出摘要和耗时。
- Better test coverage（更完整测试覆盖）：补充 service、repository 和关键 fallback 流程测试。

## Not Planned（不计划做的事情）

- 自动投递。
- 自动发送招聘消息。
- 批量爬取岗位。
- 读取 Cookie / Token。
- 绕过验证码、登录校验或反爬机制。
