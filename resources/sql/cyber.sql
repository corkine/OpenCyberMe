------------------------ auto ----------------------
-- :name set-today-auto :! :1
insert into auto (r1start, r1end, r2start, r2end)
values (:start1, :end1, :start2, :end2);
-- :name set-auto :! :1
insert into auto (day, r1start, r1end, r2start, r2end)
values (:day, :start1, :end1, :start2, :end2);
-- :name update-auto-info :! :1
update auto
set info = :info
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

------------------------ task ----------------------
-- :name add-job :! :1
insert into task (task_id, job_info)
values (:task_id, :job_info);
-- :name delete-job :! :1
delete
from task
where job_id = :job_id;
-- :name task-all-jobs :? :*
select *
from task
where task_id = :task_id;
-- :name update-job :! :1
update task
set job_info = job_info || :job_info,
    job_status = :job_status
where job_id = :job_id;
-- :name next-queued-job :? :1
select *
from task
where task_id = :task_id
and job_status = 'queued'
order by create_at
limit 1;
-- :name all-need-retry :? :*
select *
from task
where (job_status = 'failed' and (job_info->>'job_rest_try')::integer > 0)
or (job_status = 'dispatched' and (job_info->>'job_rest_try')::integer > 0
    and to_timestamp((job_info->>'dispatch_will_return')::double precision/1000) < current_timestamp);
-- :name dispatched-and-no-try :? :*
select *
from task
where (job_status = 'dispatched' and (job_info->>'job_rest_try')::integer <= 0);

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
insert into express(no, track, info, update_at)
values (:no, :track, :info, current_timestamp)
on conflict (no) do update set track     = :track,
                               info      = :info,
                               update_at = current_timestamp;
-- :name delete-express :! :1
delete
from express
where no = :no;
-- :name update-express :! :1
update express
set track     = :track,
    info      = :info,
    update_at = current_timestamp
where no = :no;
-- :name find-express :? :1
select *
from express
where no = :no
limit 1;
-- :name recent-express :? :*
select no                       as id,
       info ->> 'note'          as name,
       (info ->> 'status')::int as status,
       update_at                as last_update,
       track                    as info
from express
order by create_at desc
limit 10;

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
-- :name to-do-recent-day-2 :? :*
select title,
       info -> 'listInfo' ->> 'name'                                                    as list,
       info ->> 'status'                                                                as status,
       info ->> 'importance'                                                            as importance,
       coalesce(((info -> 'dueDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval)::text,
                ((info -> 'completedDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval)::text,
                (info ->> 'createdDateTime')::text)::date                               as time,
       (info ->> 'createdDateTime')::timestamptz                                        as create_at,
       ((info -> 'completedDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval) as finish_at,
       ((info -> 'dueDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval)       as due_at,
       (info ->> 'lastModifiedDateTime')::timestamptz                                   as modified_at
from todo
where ((info ->> 'createdDateTime')::timestamptz >
       (current_timestamp - (:day || ' day')::interval))
   or (((info -> 'dueDateTime' ->> 'dateTime')::timestamptz + '8 hour'::interval))::timestamptz >
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
set info      = :info,
    update_at = current_timestamp
where id = :id;
-- :name delete-movie :! :1
delete
from movie
where id = :id;
-- :name recent-movie-update :? :*
select name             as name,
       url              as url,
       info -> 'series' as data,
       update_at        as last_update
from movie
where update_at > (current_date - (:day || ' day')::interval)
order by update_at desc;

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
-- :name set-someday-info :! :1
insert into days (day, info, update_at)
values (:day, :info, current_timestamp)
on conflict (day) do update set info      = days.info || :info,
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
delete
from fitness
where id = :id;
-- :name details-fitness :? :1
select *
from fitness
where id = :id
limit 1;
-- :name all-fitness :? :*
select *
from fitness
limit :limit;
-- :name all-fitness-after :? :*
select *
from fitness
where start at time zone 'Asia/Shanghai' >= :day;
-- :name all-fitness-after-limit :? :*
select *
from fitness
where start at time zone 'Asia/Shanghai' >= :day
limit :limit;
-- :name all-fitness-by-cat-after-limit :? :*
select *
from fitness
where start at time zone 'Asia/Shanghai' >= :day
  and category = :category
limit :limit;
-- :name insert-fitness-batch :! :*
insert into fitness (category, value, unit, start, "end", duration, hash)
values :tuple*:records
on conflict (hash)
do update set "end" = excluded."end",
              value = excluded.value,
              duration = excluded.duration;
-- :name remote-all-fitness :! :*
delete
from fitness
where 1 = 1;
-- :name recent-activity :? :*
select date(start at time zone 'Asia/Shanghai'), category, sum(value)
from fitness
where (category = 'restactivity' or category = 'activeactivity' or category = 'dietaryenergy')
  and start at time zone 'Asia/Shanghai' > (current_date - (:day || ' day')::interval)
group by date(start at time zone 'Asia/Shanghai'), category
order by date(start at time zone 'Asia/Shanghai') desc;

---------------------- Diary -------------------
-- :name all-diary :? :*
select * from diary
order by (info->>'day')::date desc, create_at desc
limit 100;
-- :name range-diary :? :*
select * from diary
order by (info->>'day')::date desc, create_at desc
limit :take
offset :drop;
-- :name diaries-range :? :*
select * from diary
where ((info->>'day')::date >= :start) and ((info->>'day')::date <= :end)
order by (info->>'day')::date;
-- :name diary-by-id :? :1
select * from diary
where id = :id;
-- :name diaries-by-day :? :*
select * from diary
where (info->>'day')::date = :day;
-- :name diaries-by-label :? :*
select * from diary
where :label = any((info->>'labels')::text[]);
-- :name insert-diary :! :1
insert into diary (title, content, info)
values (:title, :content, :info);
-- :name update-diary :! :1
update diary set title = :title,
                 content = :content,
                 info = :info,
                 update_at = current_timestamp
where id = :id;
-- :name delete-diary :! :1
delete from diary
where id = :id;

--------------------- books ---------------------
-- :name insert-books-batch :! :*
insert into books (uuid, title, author, info)
values :tuple*:books
    on conflict (uuid)
do update set title = excluded.title,
           author = excluded.author,
           info = books.info || excluded.info,
           modified_at = current_timestamp;
-- :name find-book-by-title :? :*
select * from books
where title ilike ('%'|| :search ||'%');
-- :name find-book-by-author :? :*
select * from books
where author ilike ('%' || :search || '%');
-- :name find-book-by-title-author :? :*
select * from books
where author ilike ('%' || :search || '%') or title ilike ('%'|| :search ||'%');
-- :name get-book :? :1
select *
from books
where uuid = :id;
-- :name drop-all-books :! :1
truncate books;

--------------------- disks ---------------------
-- :name insert-files-batch :! :*
insert into files(path, name, size, info)
values :tuple*:files
    on conflict (path)
do update set name = excluded.name,
           size = excluded.size,
           info = files.info || excluded.info,
           create_at = current_timestamp;
-- :name find-file-by-path :? :*
select * from files
where path ilike ('%'|| :search ||'%')
  and info->>'type' = 'FILE'
order by info->>'last-modified' desc
limit :take
offset :drop;
-- :name find-file-by-name :? :*
select * from files
where name ilike ('%' || :search || '%')
  and info->>'type' = 'FILE'
order by info->>'last-modified' desc
limit :take
offset :drop;
-- :name find-path :? :*
select * from files
where path ilike ('%' || :search || '%')
order by info->>'last-modified' desc
limit :take
offset :drop;
-- :name get-file :? :1
select *
from files
where id = :id;
-- :name drop-all-files :! :1
truncate files;
-- :name drop-disk-files :! :1
delete from files
where info->>'disk' = :disk;