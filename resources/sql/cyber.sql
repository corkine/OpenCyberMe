-- :name set-today-auto :! :1
insert into auto (r1start, r1end, r2start, r2end)
values (:start1, :end1, :start2, :end2);
-- :name get-today-auto :? :1
select *
from auto
where day = :day;
-- :name get-today-signin :? :1
select *
from signin
where day = current_date
limit 1;
-- :name get-signin :? :1
select *
from signin
where day = :day
limit 1;
-- :name set-signin :! :1
insert into signin (day, hcm)
values (:day, :hcm);
-- :name update-signin-hcm :! :1
update signin
set hcm = :hcm
where day = :day;
-- :name all-express :? :*
select *
from express;
-- :name need-track-express :? :*
select *
from express
where info ->> 'status' = '1';
-- :name set-express-track :! :1
insert into express(no, track, info)
values (:no, :track, :info)
on conflict (no) do update set track = :track,
                               info  = :info;
-- :name delete-express :! :1
delete
from express
where no = :no;
-- :name update-express :! :1
update express
set track = :track,
    info  = :info
where no = :no;
-- :name find-express :? :1
select * from express
where no = :no
limit 1;