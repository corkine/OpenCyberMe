-- :name all-place :? :*
select places.id as placeId, places.updateat as placeupdateat,
       place, location, description, g.id, g.uid,
       g.name,
       g.info->>'note' as note,
       g.info->'labels' as labels,
       g.info->>'status' as status, g.createat, g.updateat from places
left join goods g on places.id = g.placeid
where (g.info->'hide')::bool is null or (g.info->'hide')::bool = false;
-- :name add-place :! :1
insert into places(place, location, description)
values (:place, :location, :description);
-- :name add-package :! :1
insert into packages (name, info)
values (:name, :info);
-- :name delete-good :! :1
delete from goods where id = :id;
-- :name hide-good :! :1
update goods set info = info || '{"hide":true}'::jsonb
where id = :id;