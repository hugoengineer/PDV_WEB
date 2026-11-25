#!/usr/bin/env bash
# Executado pela imagem oficial do PostgreSQL apenas na primeira inicialização (volume vazio).
set -euo pipefail

: "${DB_MIGRACAO_PASSWORD:?Defina DB_MIGRACAO_PASSWORD no .env}"
: "${DB_APP_PASSWORD:?Defina DB_APP_PASSWORD no .env}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v senha_migracao="$DB_MIGRACAO_PASSWORD" -v senha_app="$DB_APP_PASSWORD" \
     -f /docker-entrypoint-initdb.d/papeis.sql.inc

echo "Papéis pdv_migracao e pdv_app criados."
