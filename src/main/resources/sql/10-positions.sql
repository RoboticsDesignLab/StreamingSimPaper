create table positions
(
    id serial not null
        constraint positions_pkey
            primary key,
    name varchar not null,
    time bigint not null,
    x double precision not null,
    y double precision not null,
    z double precision not null
);

alter table positions owner to bohm;

create index positions_time_ind
    on positions (time);

