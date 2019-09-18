DROP VIEW distances;
CREATE VIEW distances AS
    SELECT test_group, label, name, array_to_json(
        array_agg( ARRAY[time, round( SQRT( POWER(my_pos_x - opp_pos_x, 2) + POWER(my_pos_y - opp_pos_y, 2))::numeric, 2) ] ORDER BY time)
    ) FROM steering_decisions GROUP BY label, name, test_group order by test_group, name, label;

DROP VIEW trajectories;
CREATE VIEW trajectories AS
    SELECT test_group, run, label, name, array_to_json(
            array_agg(ARRAY [ROUND(my_pos_x::numeric, 3), ROUND(my_pos_y::numeric, 3)] ORDER BY time)
        ) AS points
    FROM steering_decisions
    GROUP BY label, run, name, test_group ORDER BY test_group, label, run, name;
