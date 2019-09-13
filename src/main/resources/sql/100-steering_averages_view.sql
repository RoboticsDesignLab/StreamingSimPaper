drop view steering_averages;
create view steering_averages AS
select substring(label, 0, 15) AS label, run, name,
       count(*) AS points,
       max(time) AS runtime,
       round(avg(my_loc_recency)::numeric, 3) my_loc_recency,
       round(max(my_loc_recency::numeric), 3) AS my_loc_max,
       round(avg(opp_loc_recency::numeric), 3) as opp_loc_recency,
       round(avg(since_my_last_move::numeric), 3) AS since_my_last_move,
       round(avg(since_opp_last_move::numeric), 3) AS since_opp_last_move
from steering_decisions_location_recency
group by label, name, run order by label, run, name;
