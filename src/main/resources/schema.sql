drop table if exists My_User;

CREATE TABLE if not exists My_User (id UUID PRIMARY KEY, authentication_id varchar,
 first_name varchar, last_name varchar, email varchar,
 birth_date timestamp, profile_photo varchar, gender_id UUID, active boolean);