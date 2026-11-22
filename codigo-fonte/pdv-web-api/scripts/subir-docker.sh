#!/usr/bin/env bash
# Sobe o ambiente completo (PostgreSQL + API + backup) e confere se ficou saudável.
# Uso: bash scripts/subir-docker.sh [CPF_DO_GERENTE]   (o CPF só é usado se o .env ainda não existir)
set -euo pipefail
cd "$(dirname "$0")/.."

if ! docker info > /dev/null 2>&1; then
    echo "O Docker não está respondendo. Abra o Docker Desktop, espere aparecer 'Engine running' e rode de novo." >&2
    exit 1
fi

if [ ! -f .env ]; then
    bash scripts/criar-env.sh "${1:-}"
fi

docker compose up -d --build

echo "Aguardando o banco e a API ficarem saudáveis (até 5 min)..."
for _ in $(seq 1 60); do
    banco=$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q banco)" 2>/dev/null || echo "?")
    api=$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q api)" 2>/dev/null || echo "?")
    if [ "$banco" = "healthy" ] && [ "$api" = "healthy" ]; then
        docker compose ps
        echo
        echo "Pronto. API: http://localhost:8080  ·  saúde: http://localhost:8080/actuator/health"
        # O banco exige senha até no socket local (scram-sha-256): usa a do próprio container
        docker compose exec -T banco sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d pdv_web -c \
            "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"'
        ls -1 backups/ 2>/dev/null | tail -3 | sed 's/^/Backup: /'
        exit 0
    fi
    sleep 5
done

echo "O ambiente não ficou saudável a tempo. Veja: docker compose logs banco api" >&2
docker compose ps
exit 1
