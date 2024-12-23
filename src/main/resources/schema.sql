
CREATE TABLE if not exists My_User (id UUID PRIMARY KEY, authentication_id varchar,
 first_name varchar, last_name varchar, email varchar,
 birth_date timestamp, gender_id UUID, active boolean, user_auth_account_created boolean,
 searchable boolean);

alter table My_User drop column if exists profile_photo_file_key;

alter table My_User drop column if exists thumbnail_file_key;

alter table My_User drop column if exists profile_photo;

alter table My_User add column if not exists profile_photo json;
