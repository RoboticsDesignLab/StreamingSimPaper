create type point2d AS (
    x double precision,
    y double precision
    );

DROP FUNCTION IF EXISTS rel_distance(x1 double precision, y1 double precision, t1 double precision, x2 double precision, y2 double precision, t2 double precision);
CREATE FUNCTION rel_distance(x1 double precision, y1 double precision, t1 double precision, x2 double precision, y2 double precision, t2 double precision) RETURNS point2d AS $$
SELECT ( (x2 - x1) * COS(t1) + (y2 - y1) * SIN(t1), -(x2 - x1) * SIN(t1) + (y2 - y1) * COS(t1) )::point2d ;
$$ LANGUAGE SQL IMMUTABLE ;
