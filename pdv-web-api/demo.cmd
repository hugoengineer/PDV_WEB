@echo off
rem Sobe a API em MODO DEMONSTRACAO (PostgreSQL embarcado, dados de exemplo, Swagger UI).
rem Acesse http://localhost:8080/swagger-ui.html  -  Ctrl+C para encerrar.
call "%~dp0mvnw.cmd" -q spring-boot:test-run "-Dspring-boot.run.main-class=br.com.empresa.pdv.DemonstracaoPdvWebApi"
