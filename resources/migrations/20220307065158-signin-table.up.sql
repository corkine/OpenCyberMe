create table signin (
    id serial primary key,
    day date default current_date unique,
    hcm jsonb default '{}'::jsonb,
    info jsonb default '{}'::jsonb,
    update_at timestamptz default current_timestamp
);