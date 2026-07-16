# AI 求职 Agent 静态工作台

仓库此前没有独立的前端工程。本目录提供零构建依赖的静态工作台，直接调用现有 Spring Boot API，包含 Dashboard、简历管理、岗位分析、历史记录和 Trace/设置预留入口。

## 本地运行

在仓库根目录执行：

```powershell
python -m http.server 5173 -d frontend
```

然后访问 `http://localhost:5173`，右上角 API Base 默认是 `http://localhost:8080`。后端已允许 `localhost/127.0.0.1` 的 5173 和 3000 开发端口。
