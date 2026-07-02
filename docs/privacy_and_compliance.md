# Privacy & Compliance

AI Job Screening Agent is designed as a local-first personal job-search tracking and analysis tool. It helps the user organize job information already visible in the browser and decide what to follow up manually.

## Data Source Boundary

The userscript only reads text that is already visible on the current browser page.

It does not:

- read BOSS Cookie / Token;
- access BOSS non-public APIs;
- bypass verification or login checks;
- apply to jobs automatically;
- send messages automatically;
- perform platform data collection.

## User Control

The user remains in control of all important actions:

- AI analysis only runs after the user clicks the AI 深度核验 button.
- Application feedback is saved only after the user clicks the save button.
- Whether to apply, reply, follow up, or ignore a job is decided manually by the user.

## Local Backend

The backend runs locally by default and receives only the job information submitted by the userscript.

Stored data may include:

- job title;
- company name;
- salary;
- city;
- schedule and duration;
- visible job description text;
- rule score and rule conclusion;
- AI analysis result;
- user feedback notes.

Do not store or upload real private chat records, screenshots, phone numbers, or personal contact details in a public repository.

## Secrets

The repository provides `.env.example` as a template only. It must not contain real values.

Never commit:

- real `DEEPSEEK_API_KEY`;
- MySQL password;
- Redis password;
- `.env`;
- Cookie;
- Token.

## Public Demo Data

All examples in the public repository should use mock data.

Good examples:

- 示例科技
- 测试智能
- 某招聘者
- mock job descriptions

Avoid:

- real HR names;
- real company communication;
- real screenshots;
- exported personal chat logs.

## AI Output Boundary

AI output is advisory. The model can help summarize fit, risks, resume matches, and interview focus, but it does not make final decisions for the user.

The final decision to apply, reply, follow up, or reject a job should always be made manually.
