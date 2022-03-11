create table fitness (
    id serial primary key,
    category varchar(255),
    value double precision default 0.0,
    unit varchar(255),
    start timestamptz,
    "end" timestamptz,
    duration int,
    info jsonb default '{}'::jsonb,
    last_update timestamptz default current_timestamp,
    hash varchar(255) unique
);