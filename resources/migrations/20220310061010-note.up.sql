create table note (
    id bigint primary key,
    "from" varchar(255),
    content text,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
);