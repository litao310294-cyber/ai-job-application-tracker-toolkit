# Excel Design

The workbook is designed as a lightweight personal job application tracker.

## Sheets

### Applications

Main data table for application records.

Fields:

`ID, Application Date, Platform, Company, Position, City, Company Size, Salary Range, Job Link, Priority Tier, Job Type, JD Keywords, Match Level, Contacted Proactively, Opening Message Version, Resume Version, Current Status, Read Time, Reply Time, Interview Time, Interview Round, Interview Result, Blocker or Rejection Reason, Next Action, Notes`

### Dashboard

Formula-based summary sheet.

Metrics:

- total applications
- today's applications
- read count
- reply count
- interview count
- rejection count
- offer count
- reply rate
- interview rate
- rejection rate
- status distribution

### Daily Review

Daily reflection table for job-search review.

### Interview Review

Structured notes for interview questions, weak points, and preparation tasks.

### Config

Dropdown option source sheet.

## Formatting

- Header rows use dark background and white text.
- Dropdowns are used for status and category fields.
- Conditional formatting highlights high-priority jobs, rejections, replies, interviews, and read-no-reply rows.
- The dashboard uses formulas and should not be overwritten manually.

## Data Principle

The template file contains no real application data. Use `examples/mock_tracker.xlsx` for demo data.
