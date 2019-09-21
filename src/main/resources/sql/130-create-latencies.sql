create table latencies (
    id serial primary key not null,
    label varchar not null,
    latency double precision not null,
    test_group varchar not null
                       );
