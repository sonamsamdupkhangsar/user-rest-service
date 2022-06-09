CREATE TABLE if not exists User (id UUID PRIMARY KEY, first_name varchar, last_name varchar, email varchar,
 birth_date timestamp, profile_photo varchar, gender_id UUID);