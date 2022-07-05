create table task
(
    job_id     serial primary key,
    task_id    varchar(255) not null,
    job_status varchar(255) not null default 'queued',
    job_info   jsonb                 default '{}'::jsonb,
    create_at  timestamptz           default current_timestamp
)