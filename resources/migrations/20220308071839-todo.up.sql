create table todo (
    id varchar(255) primary key,
    title varchar(255) not null,
    info jsonb default '{}'::jsonb,
    note jsonb default '{}'::jsonb,
    last_update timestamptz default current_timestamp
);