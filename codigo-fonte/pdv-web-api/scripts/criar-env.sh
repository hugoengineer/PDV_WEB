#!/usr/bin/env bash
# Gera o pdv-web-api/.env com senhas aleatórias fortes. Não sobrescreve um .env existente.
# Uso: bash scripts/criar-env.sh [CPF_DO_GERENTE]
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
    echo ".env já existe — nada foi alterado. Apague-o primeiro se quiser gerar novas senhas" >&2
    echo "(atenção: com o banco já criado, as senhas dos usuários do banco NÃO mudam junto)." >&2
    exit 1
fi

senha() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
CPF="${1:-}"
if [ -z "$CPF" ]; then
    read -r -p "CPF do gerente inicial (só números): " CPF
fi
SENHA_GERENTE="Pdv-$(senha 12)"

sed -e "s|^DB_ADMIN_PASSWORD=.*|DB_ADMIN_PASSWORD=$(senha 32)|" \
    -e "s|^DB_MIGRACAO_PASSWORD=.*|DB_MIGRACAO_PASSWORD=$(senha 32)|" \
    -e "s|^DB_APP_PASSWORD=.*|DB_APP_PASSWORD=$(senha 32)|" \
    -e "s|^PDV_JWT_SEGREDO=.*|PDV_JWT_SEGREDO=$(senha 64)|" \
    -e "s|^PDV_ADMIN_CPF=.*|PDV_ADMIN_CPF=$CPF|" \
    -e "s|^PDV_ADMIN_SENHA=.*|PDV_ADMIN_SENHA=$SENHA_GERENTE|" \
    .env.exemplo > .env
chmod 600 .env 2>/dev/null || true

echo ".env criado com senhas aleatórias."
echo "Login do gerente inicial: CPF $CPF / senha $SENHA_GERENTE  (troque no primeiro acesso)"
