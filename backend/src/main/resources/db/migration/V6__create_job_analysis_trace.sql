create table if not exists job_analysis_trace (
  id bigint primary key auto_increment,
  task_id varchar(64) not null,
  job_record_id bigint null,
  stage varchar(40) not null,
  input_data longtext null,
  output_data longtext null,
  latency_ms bigint null,
  created_time datetime not null default current_timestamp,
  index idx_job_analysis_trace_task_id (task_id),
  index idx_job_analysis_trace_job_record_id (job_record_id),
  index idx_job_analysis_trace_task_stage (task_id, stage)
);
