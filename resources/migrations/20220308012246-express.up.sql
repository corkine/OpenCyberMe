create table express (
    id serial primary key,
    no varchar(255) not null unique,
    track jsonb default '{}'::jsonb,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
);