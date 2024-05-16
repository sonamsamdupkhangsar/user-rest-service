CREATE TABLE if not exists My_User (id UUID PRIMARY KEY, authentication_id varchar,
 first_name varchar, last_name varchar, email varchar,
 birth_date timestamp, profile_photo varchar, gender_id UUID, active boolean, user_auth_account_created boolean,
 searchable boolean);