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