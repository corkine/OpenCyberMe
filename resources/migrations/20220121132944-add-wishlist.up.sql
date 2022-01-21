create table wishlist
(
    id     serial primary key,
    client   varchar(100),
    kind   varchar(30),
    advice text not null,
    info jsonb default '{}'::jsonb,
    add_time timestamptz default current_timestamp
);