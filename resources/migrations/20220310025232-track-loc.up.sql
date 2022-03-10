create table track (
    id serial primary key,
    by varchar(255) not null,
    info jsonb default '{}'::jsonb,
    update_time timestamptz default current_timestamp
);