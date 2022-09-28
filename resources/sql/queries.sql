-- :name recent-logs :? :*
select *
from logs
order by record_ts desc
limit 100;
-- :name logs-between :? :*
select *
from logs
where record_ts <= :end and record_ts >= :start
/*~ (if (:api params) */
and logs.api ~* :api
/*~*/
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
-- :name last-10-edit :? :*
select info ->> 'remote-addr' as from, 'post' as method, api, record_ts as time
from logs
where info ->> 'request-method' = 'post'
order by record_ts desc
limit 10;
-- :name insert-wishlist :! :1
insert into wishlist(client, kind, advice)
values (:client, :kind, :advice)
returning *;
-- :name find-all-wish :? :*
select *
from wishlist
where kind = '愿望' or kind = 'BUG'
order by add_time desc
limit 50;

