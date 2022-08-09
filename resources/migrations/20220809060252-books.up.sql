create table if not exists books
(
    uuid        varchar(255) primary key,
    title       varchar(255) not null,
    author      varchar(255),
    info        jsonb       default '{}'::jsonb,
    create_at   timestamptz default current_timestamp,
    modified_at timestamptz default current_timestamp
)