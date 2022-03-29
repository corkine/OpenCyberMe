create table diary (
    id serial primary key,
    title varchar(255) default '',
    content text default '',
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
)