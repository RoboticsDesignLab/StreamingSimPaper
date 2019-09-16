drop view steering_decisions_location_recency CASCADE;

create view steering_decisions_location_recency AS
select label, run, name, time,
       time - min(time) over (partition by run) as relative_time,
       rank() OVER (partition by run, name ORDER BY time) AS rank_by_name,
       time - my_pos_time AS my_loc_recency,
       time - opp_pos_time AS opp_loc_recency,
       s.time - (SELECT MAX(sb.time) from steering_decisions sb WHERE sb.run = s.run and sb.time < s.time AND s.name = sb.name) AS since_my_last_move,
       s.time - (SELECT MAX(sb.time) from steering_decisions sb WHERE sb.run = s.run and sb.time < s.time AND s.name != sb.name) AS since_opp_last_move
from steering_decisions s
ORDER BY time
;
