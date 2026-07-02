# Roadmap

This roadmap lists planned improvements. Items here are not implemented unless explicitly marked as completed in README or API documentation.

## Completed

- Browser userscript for visible job information extraction.
- Local rule-based job fit scoring.
- AI 深度核验 through local Spring Boot backend.
- DeepSeek OpenAI-compatible LLM integration.
- MySQL persistence for job records, analysis results, and feedback.
- Redis cache for job analysis results.
- User profile and scoring config.
- Profile RAG-Lite keyword retrieval.
- RAG-Lite integration with `/api/job/analyze`.
- profileRag evidence returned by backend and displayed in the userscript.
- History record query and job match endpoints.
- Historical analysis and feedback included in RAG-Lite reindex.
- Field sanitization for dirty companyName / jobTitle values.

## Planned

- Manual user profile editing page.
- Dashboard for application count, reply rate, interview rate, direction distribution, and rejection reasons.
- Interview review records entering RAG-Lite.
- Better page extraction resilience when the platform UI changes.
- Multi-profile or multi-user isolation.
- PDF resume parsing.
- embedding-based retrieval.
- Vector database integration.
- Rerank for profile evidence retrieval.

## Not Planned For The Public Demo

- Automatic job applications.
- Automatic message sending.
- Reading Cookie / Token.
- Accessing BOSS non-public APIs.
- Bypassing verification or login checks.

The project should stay focused on personal job-search tracking, visible page information organization, and AI-assisted analysis.
