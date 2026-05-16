-- Postgres init (docker-entrypoint-initdb.d): core Impilo vNext databases.
-- Runs only on first cluster init; add new CREATE DATABASE lines when introducing services.

CREATE DATABASE experience_bff;
CREATE DATABASE tshepo;
CREATE DATABASE vito;
CREATE DATABASE varapi;
CREATE DATABASE tuso;
CREATE DATABASE mvumo;
CREATE DATABASE zibo;
CREATE DATABASE pct;
CREATE DATABASE oros;
CREATE DATABASE pharmacy;
CREATE DATABASE impilo_learning;
-- NDILA — Geospatial Intelligence, Routing, Location & Spatial Orchestration.
-- The schema bootstrap (Flyway V001) conditionally enables PostGIS if the
-- extension is available on the host cluster. When running against a
-- postgis/postgis image, PostGIS columns are used; otherwise Ndila falls
-- back to JSONB geometry storage + pure-Java spatial math (see GeoMath /
-- GeoJsonReader). Either mode produces a working dev environment.
CREATE DATABASE impilo_ndila;
