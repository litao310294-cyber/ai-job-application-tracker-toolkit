alter table user_profile_chunk add column embedding_json text;
alter table user_profile_chunk add column embedding_model varchar(100);
alter table user_profile_chunk add column embedding_dimension int;
alter table user_profile_chunk add column embedding_status varchar(30);
alter table user_profile_chunk add column embedding_content_hash varchar(128);
alter table user_profile_chunk add column embedded_at datetime;
