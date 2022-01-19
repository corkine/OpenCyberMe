create table features
(
    id          serial primary key,
    rs_id       varchar(30)  unique not null,
    title       varchar(100) not null,
    description text         not null default '',
    version     varchar(30)  not null default 'ICE 5.0',
    info        jsonb        not null default '{}'::jsonb,
    -- 包含维护人员，开发人员，测试人员，经理，接口和其他文档信息
    create_at   timestamptz           default current_timestamp,
    update_at   timestamptz           default current_timestamp
);

-- insert into features (rs_id, title, description)
-- values ('TIMEMACHINE', '配置时光机', '配置时光机描述');
--
-- select *
-- from features
-- where info ->> 'developer' = 'Corkine Ma';
--
-- update features
-- set info = info || '{
--   "developer": "Corkine Ma",
--   "test": "JiangLin"
-- }'::jsonb
-- where rs_id = 'TIMEMACHINE';