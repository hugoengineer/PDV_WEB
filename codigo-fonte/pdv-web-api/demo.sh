#!/bin/sh
# PDV Web - MODO DEMONSTRACAO: PostgreSQL embarcado, dados de exemplo e front-end.
# O navegador abre sozinho em http://localhost:8080 quando a aplicacao fica pronta.
# Gerente: 111.444.777-35 / Gerente@Demo1   Vendedor: 529.982.247-25 / Vendedor@123
# Ctrl+C para encerrar (os dados sao apagados).
cd "$(dirname "$0")" || exit 1

# Versao principal do java de uma pasta (0 se nao houver)
versao() {
  [ -x "$1/bin/java" ] || [ -x "$1/bin/java.exe" ] || { echo 0; return; }
  "$1/bin/java" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

# Procura um Java 25+: JAVA_HOME, JDK do IntelliJ (Windows), JDKs comuns, java do PATH
if [ "$(versao "${JAVA_HOME:-/nao-existe}")" -lt 25 ] 2>/dev/null || [ -z "$JAVA_HOME" ]; then
  for candidato in "/c/Program Files/JetBrains/"IntelliJ*/jbr /usr/lib/jvm/* /Library/Java/JavaVirtualMachines/*/Contents/Home \
                   "$(dirname "$(dirname "$(command -v java 2>/dev/null || echo /x/x/x)")")"; do
    if [ "$(versao "$candidato")" -ge 25 ] 2>/dev/null; then export JAVA_HOME="$candidato"; break; fi
  done
fi
echo "Java: ${JAVA_HOME:-(PATH)}"
echo "Iniciando o PDV Web... o navegador abre sozinho quando estiver pronto."

./mvnw -q spring-boot:test-run "-Dspring-boot.run.main-class=br.com.empresa.pdv.DemonstracaoPdvWebApi"
