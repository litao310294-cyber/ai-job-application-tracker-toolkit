-- Apply once to an existing database created from the original schema.sql.
alter table job_record
  add column education varchar(100),
  add column experience varchar(100),
  add column skills_json text,
  add column tags_json text,
  add column capture_source varchar(20);
