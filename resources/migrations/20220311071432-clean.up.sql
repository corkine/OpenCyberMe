create table days (
    day date primary key default current_date,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
);