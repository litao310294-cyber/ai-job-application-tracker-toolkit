create table if not exists job_record (
  id bigint primary key auto_increment,
  job_title varchar(255),
  company_name varchar(255),
  salary varchar(100),
  city varchar(100),
  schedule_text varchar(100),
  duration_text varchar(100),
  job_text text,
  rule_score int,
  rule_conclusion varchar(50),
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp
);

create table if not exists job_analysis (
  id bigint primary key auto_increment,
  job_record_id bigint not null,
  task_id varchar(100),
  status varchar(50),
  decision varchar(50),
  score int,
  direction varchar(255),
  reasons_json text,
  risks_json text,
  resume_matches_json text,
  interview_focus_json text,
  suggested_message text,
  created_at datetime not null default current_timestamp,
  index idx_job_analysis_record_id (job_record_id),
  index idx_job_analysis_created_at (created_at),
  constraint fk_job_analysis_record
    foreign key (job_record_id) references job_record (id)
    on delete cascade
);
