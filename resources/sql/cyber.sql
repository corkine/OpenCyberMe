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

-- :name all-to-do :? :*
select * from todo;
-- :name to-do-modify-in-2-days :? :*
select id, title from todo
where (info->>'lastModifiedDateTime')::timestamptz >
      (current_timestamp - '2 day'::interval);
-- :name to-do-all :? :*
select title, info->'listInfo'->>'name' as list, info->>'status' as status,
       info->>'importance' as importance,
       (info->>'createdDateTime')::timestamptz as create_at,
       ((info->'completedDateTime'->>'dateTime')::timestamptz + '8 hour'::interval) as finish_at,
       ((info->'dueDateTime'->>'dateTime')::timestamptz + '8 hour'::interval) as due_at,
       (info->>'lastModifiedDateTime')::timestamptz as modified_at
from todo
order by (info->>'lastModifiedDateTime')::timestamptz desc ;
-- :name to-do-recent-day :? :*
select title, info->'listInfo'->>'name' as list, info->>'status' as status,
       info->>'importance' as importance,
       (info->>'createdDateTime')::timestamptz as create_at,
       ((info->'completedDateTime'->>'dateTime')::timestamptz + '8 hour'::interval) as finish_at,
       ((info->'dueDateTime'->>'dateTime')::timestamptz + '8 hour'::interval) as due_at,
       (info->>'lastModifiedDateTime')::timestamptz as modified_at
from todo
where (info->>'createdDateTime')::timestamptz >
      (current_timestamp -  (:day || ' day')::interval)
order by (info->>'lastModifiedDateTime')::timestamptz desc;
-- :name delete-by-id :! :1
delete from todo
where id = :id;
-- :name insert-to-do :! :1
insert into todo (id, title, info, last_update)
values (:id, :title, :info, current_timestamp)
on conflict (id) do update set title = :title,
                               info = :info;