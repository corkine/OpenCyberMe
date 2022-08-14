create table if not exists files
(
    id serial primary key,
    path varchar(1000) not null,
    name varchar(255) not null,
    size bigint default 0,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp
);

--;;

create index idx_disk_files_path on files using btree(path varchar_pattern_ops);

--;;

create index idx_disks_files_name on files using btree(name varchar_pattern_ops);