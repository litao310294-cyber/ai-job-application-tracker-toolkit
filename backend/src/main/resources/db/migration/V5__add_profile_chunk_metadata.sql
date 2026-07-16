alter table user_profile_chunk add column chunk_type varchar(30);
alter table user_profile_chunk add column chunk_weight decimal(5,2);
alter table user_profile_chunk add column metadata_json text;
