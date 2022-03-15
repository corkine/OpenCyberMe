------------------------ auto ----------------------
-- :name set-today-auto :! :1
insert into auto (r1start, r1end, r2start, r2end)
values (:start1, :end1, :start2, :end2);
-- :name set-auto :! :1
insert into auto (day, r1start, r1end, r2start, r2end)
values (:day, :start1, :end1, :start2, :end2);
-- :name update-auto-info :! :1
update auto set info = :info
where day = :day;
-- :name list-auto-recent :? :*
select *
from auto
where day::date > (current_date - (:day || ' day')::interval);
-- :name delete-auto :! :1
delete
from auto
where day = :day;
-- :name get-today-auto :? :1
select *
from auto
where day = :day;

------------------------ signin ----------------------
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
values (:day, :hcm)
on conflict (day) do update set hcm = :hcm;
-- :name update-signin-hcm :! :1
update signin
set hcm = :hcm
where day = :day;

------------------------ express ----------------------
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
select *
from express
where no = :no
limit 1;

------------------------ to-do ----------------------
-- :name all-to-do :? :*
select *
from todo;
-- :name to-do-modify-in-2-days :? :*
select id, title
from todo
where (info ->> 'lastModifiedDateTime')::timestamptz >
      (current_timestamp - '2 day'::interval);
-- :name to-do-all :? :*
select title,
       info -> 'listInfo' ->> 'name'                                                    as list,
       info ->> 'status'                                                                as status,
       info ->> 'importance'                                                            as importance,
       (info ->> 'createdDateTime')::timestamptz                                        as create_at,
       ((info -> 'completedDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval) as finish_at,
       ((info -> 'dueDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval)       as due_at,
       (info ->> 'lastModifiedDateTime')::timestamptz                                   as modified_at
from todo
order by (info ->> 'lastModifiedDateTime')::timestamptz desc;
-- :name to-do-recent-day :? :*
select title,
       info -> 'listInfo' ->> 'name'                                                    as list,
       info ->> 'status'                                                                as status,
       info ->> 'importance'                                                            as importance,
       (info ->> 'createdDateTime')::timestamptz                                        as create_at,
       ((info -> 'completedDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval) as finish_at,
       ((info -> 'dueDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval)       as due_at,
       (info ->> 'lastModifiedDateTime')::timestamptz                                   as modified_at
from todo
where (info ->> 'createdDateTime')::timestamptz >
      (current_timestamp - (:day || ' day')::interval)
order by (info ->> 'lastModifiedDateTime')::timestamptz desc;
-- :name delete-by-id :! :1
delete
from todo
where id = :id;
-- :name insert-to-do :! :1
insert into todo (id, title, info, last_update)
values (:id, :title, :info, current_timestamp)
on conflict (id) do update set title = :title,
                               info  = :info;

------------------------ track ----------------------
-- :name all-track :? :*
select *
from track;
-- :name set-track :! :1
insert into track (by, info)
values (:by, :info);

------------------------ note ----------------------
-- :name all-note :? :*
select *
from note;
-- :name insert-note :! :1
insert into note (id, "from", content, info)
values (:id, :from, :content, :info)
on conflict (id) do update set "from"  = :from,
                               content = :content,
                               info    = :info;
-- :name note-by-id :? :1
select *
from note
where id = :id
limit 1;
-- :name note-last :? :1
select *
from note
order by create_at desc
limit 1;

------------------------ movie ----------------------
-- :name all-movie :? :*
select *
from movie;
-- :name insert-movie :! :1
insert into movie (name, url)
values (:name, :url)
on conflict (name) do nothing;
-- :name update-movie :! :1
update movie
set info = :info
where id = :id;
-- :name delete-movie :! :1
delete
from movie
where id = :id;

------------------------ days ----------------------
-- :name today :? :1
select *
from days
where day = current_date;
-- :name someday :? :1
select *
from days
where day = :day;
-- :name set-today :! :1
insert into days (day, info, update_at)
values (current_date, :info, current_timestamp)
on conflict (day) do update set info      = :info,
                                update_at = current_timestamp;
-- :name set-someday :! :1
insert into days (day, info, update_at)
values (:day, :info, current_timestamp)
on conflict (day) do update set info      = :info,
                                update_at = current_timestamp;
-- :name delete-day :! :1
delete
from days
where day = :day;
-- :name day-range :? :*
select *
from days
where day >= :from
  and day <= :to
order by day desc;

------------------------ fitness ----------------------
-- :name delete-fitness :! :1
delete from fitness
where id = :id;
-- :name details-fitness :? :1
select * from fitness
where id = :id
limit 1;
-- :name all-fitness :? :*
select * from fitness
limit :limit;
-- :name all-fitness-after :? :*
select * from fitness
where start >= :day;
-- :name all-fitness-after-limit :? :*
select * from fitness
where start >= :day
limit :limit;
-- :name all-fitness-by-cat-after-limit :? :*
select * from fitness
where start >= :day and category = :category
limit :limit;
-- :name insert-fitness-batch :! :*
insert into fitness (category, value, unit, start, "end", duration, hash)
values :tuple*:records
on conflict (hash) do nothing;
-- :name remote-all-fitness :! :*
delete from fitness
where 1 = 1;