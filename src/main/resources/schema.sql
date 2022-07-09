CREATE TABLE if not exists My_User (id UUID PRIMARY KEY, first_name varchar, last_name varchar, email varchar,
 birth_date timestamp, profile_photo varchar, gender_id UUID);

 Alter table My_User Add Column If Not Exists authentication_id varchar;
