#!/usr/bin/env bash
# Copia as evidências da última execução ("./mvnw clean verify") para target/evidencias.
# Uso (na pasta pdv-web-api):  ./mvnw clean verify && bash scripts/gerar-evidencias.sh
set -euo pipefail

cd "$(dirname "$0")/.."
DESTINO=target/evidencias
mkdir -p "$DESTINO"

# 1. Um caso por linha: suíte, nome do caso (começa com o ID CT-/BDD-) e tempo em segundos
{
    printf 'suite\tcaso\ttempo_s\n'
    for xml in target/surefire-reports/TEST-*.xml; do
        grep -o '<testcase [^>]*' "$xml" |
            sed -E 's/.*name="([^"]*)".*classname="([^"]*)".*time="([^"]*)".*/\2\t\1\t\3/'
    done | sed 's/&quot;/"/g; s/&lt;/</g; s/&gt;/>/g; s/&amp;/\&/g'
} > "$DESTINO/casos-executados.tsv"

# 2. Falhas e erros (deve ficar vazio numa execução aprovada)
grep -l '<failure\|<error' target/surefire-reports/TEST-*.xml > "$DESTINO/suites-com-falha.txt" || true

# 3. Cobertura, desempenho e relatório BDD
cp target/site/jacoco/jacoco.csv "$DESTINO/cobertura-jacoco.csv"
cp target/desempenho.csv "$DESTINO/desempenho.csv"
cp target/cucumber/relatorio.html "$DESTINO/relatorio-cucumber.html"

# 4. Resumo
total=$(($(wc -l < "$DESTINO/casos-executados.tsv") - 1))
awk -F, 'NR>1{lm+=$8;lc+=$9;bm+=$6;bc+=$7} END{printf "linhas=%.1f%% ramos=%.1f%%\n",100*lc/(lc+lm),100*bc/(bc+bm)}' \
    "$DESTINO/cobertura-jacoco.csv" > "$DESTINO/resumo-cobertura.txt"
echo "Execuções registradas: $total"
echo "Suítes com falha: $(wc -l < "$DESTINO/suites-com-falha.txt")"
echo "Cobertura: $(cat "$DESTINO/resumo-cobertura.txt")"
