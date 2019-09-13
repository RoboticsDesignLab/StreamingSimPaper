create table steering_decisions
(
    id serial not null
        constraint steering_decisions_pkey
            primary key,
    name varchar not null,
    time bigint not null,
    rel_pos_x double precision not null,
    rel_pos_y double precision not null,
    phi double precision not null,
    opp_theta double precision not null,
    my_pos_x double precision,
    my_pos_y double precision,
    opp_pos_y double precision,
    opp_pos_x double precision,
    my_theta double precision,
    my_pos_time bigint,
    opp_pos_time bigint,
    run timestamp with time zone not null,
    label varchar
);

alter table steering_decisions owner to bohm;

create index steering_decisions_run_ind
    on steering_decisions (run);

create index steering_decisions_time_ind
    on steering_decisions (time);

create index steering_decisions_runtime_ind
    on steering_decisions (run, time);

create index steering_decisions_name_ind
    on steering_decisions (name);

