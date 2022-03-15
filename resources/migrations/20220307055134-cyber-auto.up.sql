create table auto (
    id serial primary key,
    day date default current_date not null unique ,
    r1start time,
    r1end time,
    r2start time,
    r2end time,
    info jsonb default '{}'::jsonb
);