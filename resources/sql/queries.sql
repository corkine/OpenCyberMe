-- :name all-features :? :*
select *
from features
order by update_at desc;
-- :name get-feature-by-rs-id :? :1
select *
from features
where rs_id ~* :rs_id;
-- :name get-feature-by-id :? :1
select *
from features
where id = :id;
-- :name insert-feature
insert into features(rs_id, title, description, version, info)
values (:rs_id, :title, :description, :version, :info);
-- :name update-info
update features
set info = info || :info
where rs_id = :rs_id;
-- :name reset-info
update features
set info = info
where rs_id = :rs_id;
-- :name update-feature :! :1
update features
set rs_id       = :rs_id,
    title       = :title,
    description = :description,
    version     = :version,
    info        = info || :info,
    update_at   = :update_at
where id = :id
returning *;
-- :name recent-logs :? :*
select *
from logs
order by record_ts desc
limit 100;
-- :name logs-between :? :*
select *
from logs
where record_ts <= :end && logs.record_ts >= :start
order by record_ts desc;
-- :name api-logs :? :*
select *
from logs
where api ~* :api
order by record_ts desc;
-- :name add-log
insert into logs(api, info)
values (:api, :info);
-- :name find-log :? :1
select *
from logs
where id = :id;
-- :name api-served-count :? :1
with addr(p) as (select info ->> 'remote-addr' from logs),
     p_addr as (select distinct * from addr)
    (select (select count(*) from p_addr) as pv,
            (select count(*) from addr)   as uv);
-- :name insert-wishlist :! :1
insert into wishlist(client, kind, advice) values
(:client,:kind,:advice) returning *;
-- :name find-all-wish :? :*
select * from wishlist
where kind = '愿望'
order by add_time desc
limit 50;