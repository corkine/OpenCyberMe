create table if not exists zlib
(
    id serial primary key,
    file_update timestamptz,
    info_update timestamptz,
    file_type varchar(20) not null,
    file_size int8 not null,
    name varchar(255) not null,
    author varchar(255),
    publisher varchar(255),
    language varchar(20),
    description varchar(255),
    publish_year int2,
    page int2,
    torrent varchar(255),
    info jsonb default '{}'::jsonb
);

--;;

create index idx_zlib_name on zlib
using btree (name);