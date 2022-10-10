create table if not exists moneySaverGoals
(
    id serial primary key,
    name varchar(255) not null,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp,
    update_at timestamptz default current_timestamp
);

--;;

create table if not exists moneySaverLogs
(
    id serial primary key,
    goal_id serial,
    info jsonb default '{}'::jsonb,
    create_at timestamptz default current_timestamp
);

--;;

alter table moneySaverLogs add constraint logs_belong_goal
    foreign key (goal_id) references moneySaverGoals(id)
        on update no action on delete cascade;