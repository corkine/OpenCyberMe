-- :name all-place :? :*
select places.id as placeId, place, location, description, g.id, g.uid,
       g.name,
       g.info->>'note' as note,
       g.info->'labels' as labels,
       g.info->>'status' as status, g.createat, g.updateat from places
left join goods g on places.id = g.placeid;