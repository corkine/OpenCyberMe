create table logs
(
    id serial primary key,
    api varchar(100),
    record_ts timestamptz default current_timestamp,
    info jsonb default '{}'::jsonb
);