#!/bin/sh
# PostgreSQL initialization (runs only when the data volume is empty).
# Spec §8.4: separate schema and DB user per service, separate database for Keycloak.
# Passwords come exclusively from environment variables (.env).
set -eu

: "${USER_SERVICE_DB_PASSWORD:?USER_SERVICE_DB_PASSWORD is required}"
: "${AUDIT_SERVICE_DB_PASSWORD:?AUDIT_SERVICE_DB_PASSWORD is required}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v user_service_pw="$USER_SERVICE_DB_PASSWORD" \
     -v audit_service_pw="$AUDIT_SERVICE_DB_PASSWORD" \
     -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" \
     -v app_db="$POSTGRES_DB" <<'EOSQL'
-- Service users
CREATE ROLE user_service LOGIN PASSWORD :'user_service_pw';
CREATE ROLE audit_service LOGIN PASSWORD :'audit_service_pw';
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_pw';

-- Application database: access for services only, no privileges in the public schema
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"app_db" TO user_service, audit_service;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Each service owns its own schema only
CREATE SCHEMA users AUTHORIZATION user_service;
CREATE SCHEMA audit AUTHORIZATION audit_service;
ALTER ROLE user_service SET search_path = users;
ALTER ROLE audit_service SET search_path = audit;

-- Keycloak: separate database and user
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
EOSQL
