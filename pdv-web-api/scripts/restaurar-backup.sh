#!/usr/bin/env bash
# Restaura um backup no banco do Docker. APAGA os dados atuais do banco pdv_web.
# Uso: bash scripts/restaurar-backup.sh backups/pdv_web_AAAAMMDD_HHMMSS.dump
set -euo pipefail
cd "$(dirname "$0")/.."
arquivo="${1:?Informe o arquivo .dump (ex.: backups/pdv_web_20261028_030000.dump)}"
[ -f "$arquivo" ] || { echo "Arquivo não encontrado: $arquivo" >&2; exit 1; }

read -r -p "Isto substitui TODOS os dados atuais por $arquivo. Digite RESTAURAR para continuar: " ok
[ "$ok" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

# No Git Bash (Windows), impede que "/backups/..." vire "C:/Program Files/Git/backups/..."
export MSYS_NO_PATHCONV=1

docker compose stop api
# A API volta mesmo se a restauração falhar (a transação única deixa o banco como estava)
trap 'docker compose start api' EXIT
docker compose exec -T backup pg_restore --clean --if-exists --no-owner --role=pdv_migracao \
    --single-transaction --exit-on-error -d pdv_web "/backups/$(basename "$arquivo")"
echo "Restauração concluída; API reiniciada."
