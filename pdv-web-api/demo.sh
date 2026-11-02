#!/bin/sh
# Sobe a API em MODO DEMONSTRACAO (PostgreSQL embarcado, dados de exemplo, Swagger UI).
# Acesse http://localhost:8080/swagger-ui.html  -  Ctrl+C para encerrar.
cd "$(dirname "$0")" && ./mvnw -q spring-boot:test-run "-Dspring-boot.run.main-class=br.com.empresa.pdv.DemonstracaoPdvWebApi"
