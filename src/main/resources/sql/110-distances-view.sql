DROP VIEW IF EXISTS distances;
CREATE VIEW distances AS
    SELECT id, run, name, label, time, rank() OVER (partition by run order by time) AS rank,
           round( SQRT( POWER(my_pos_x - opp_pos_x, 2) + POWER(my_pos_y - opp_pos_y, 2))::numeric, 2) AS distance
    FROM steering_decisions
    ORDER BY rank;
