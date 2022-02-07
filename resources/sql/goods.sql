-- :name all-place :? :*
select places.id           as placeId,
       places.updateat     as placeupdateat,
       place,
       location,
       description,
       g.id,
       g.uid,
       g.name,
       g.info ->> 'note'   as note,
       g.info -> 'labels'  as labels,
       g.info ->> 'status' as status,
       g.info -> 'packages' as packages,
       g.createat,
       g.updateat
from places
         left join goods g on places.id = g.placeid
where (g.info -> 'hide')::bool is null
   or (g.info -> 'hide')::bool = false;
-- :name add-place :! :1
insert into places(place, location, description)
values (:place, :location, :description);
-- :name add-package :! :1
insert into packages (name, info)
values (:name, :info);
-- :name delete-good :! :1
delete
from goods
where id = :id;
-- :name hide-good :! :1
update goods
set info = info || '{
  "hide": true
}'::jsonb
where id = :id;
-- :name add-good :! :1
insert into goods (uid, name, info, placeid)
values (:uid, :name, :info, :placeId);
-- :name edit-place :! :1
update places
set place       = :place,
    location    = :location,
    description = :description
where id = :id;
-- :name delete-place :! :1
delete
from places
where id = :id;
-- :name reset-goods-placeId :! :1
update goods
set placeid = '1'
where placeid = :id;
-- :name get-packages :? :*
select * from packages
where createat > now() - (:day || ' days')::interval
order by createat desc;
-- :name update-good-packages :! :1
update goods set info = jsonb_set(info,'{packages}'::text[],:packages::jsonb,true)
where id = :id;
-- :name get-good-packages :? :1
select info->'packages' as packages from goods
where id = :id;
-- :name get-package-info :? :1
select * from packages
where id = :id;