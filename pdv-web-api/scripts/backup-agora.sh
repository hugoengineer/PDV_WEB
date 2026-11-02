#!/usr/bin/env bash
# Backup manual imediato do banco do Docker para pdv-web-api/backups/.
set -euo pipefail
cd "$(dirname "$0")/.."
arquivo="pdv_web_manual_$(date +%Y%m%d_%H%M%S).dump"
docker compose exec -T backup sh -c "pg_dump --format=custom --compress=9 --file=/backups/$arquivo pdv_web && pg_restore --list /backups/$arquivo > /dev/null"
echo "Backup criado: backups/$arquivo"
