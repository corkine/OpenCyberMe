create table goods
(
    id   serial primary key,
    uid  varchar(20) unique null,
    name varchar(100) unique,
    info jsonb default '{}'::jsonb, --note status labels packages place
    createAt timestamptz default current_timestamp,
    updateAt timestamptz default current_timestamp,
    placeId serial
);

--;;

create table places
(
    id serial primary key,
    place varchar(100),
    location varchar(100),
    description text,
    createAt timestamptz default current_timestamp
);

--;;

alter table goods add constraint fk_goods_places foreign key (placeId)
references places(id) on update cascade on delete set null;

--;;

alter table goods add constraint uid_check check ( uid = upper(uid) );

--;;

create table packages
(
    id serial primary key,
    name varchar(100),
    info jsonb default '{}'::jsonb,
    createAt timestamptz default current_timestamp
);
