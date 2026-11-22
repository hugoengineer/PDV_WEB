#!/usr/bin/env bash
# Serviço "backup" do docker-compose: faz um pg_dump (formato custom, comprimido) ao subir e depois
# a cada BACKUP_INTERVALO_HORAS horas; apaga cópias com mais de BACKUP_RETENCAO_DIAS dias.
set -uo pipefail

INTERVALO_HORAS="${BACKUP_INTERVALO_HORAS:-24}"
RETENCAO_DIAS="${BACKUP_RETENCAO_DIAS:-7}"
DESTINO=/backups

fazer_backup() {
    local arquivo="$DESTINO/pdv_web_$(date +%Y%m%d_%H%M%S).dump"
    if pg_dump --format=custom --compress=9 --file="$arquivo.parcial" pdv_web; then
        mv "$arquivo.parcial" "$arquivo"
        # Confere se o arquivo é legível antes de considerar o backup válido
        if pg_restore --list "$arquivo" > /dev/null; then
            echo "$(date '+%F %T') backup ok: $(basename "$arquivo") ($(du -h "$arquivo" | cut -f1))"
        else
            echo "$(date '+%F %T') ERRO: backup ilegível, removido: $arquivo" >&2
            rm -f "$arquivo"
        fi
    else
        echo "$(date '+%F %T') ERRO: pg_dump falhou" >&2
        rm -f "$arquivo.parcial"
    fi
    find "$DESTINO" -name 'pdv_web_*.dump' -mtime +"$RETENCAO_DIAS" -print -delete
}

mkdir -p "$DESTINO"
until pg_isready -q -d pdv_web; do sleep 2; done
while true; do
    fazer_backup
    sleep $((INTERVALO_HORAS * 3600))
done
