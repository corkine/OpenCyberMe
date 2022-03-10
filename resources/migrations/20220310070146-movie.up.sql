create table movie (
    id serial primary key,
    name varchar(255) not null unique ,
    url varchar(255) not null,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
);