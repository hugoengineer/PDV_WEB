@echo off
rem ============================================================================
rem  PDV Web - MODO DEMONSTRACAO (basta dar dois cliques neste arquivo)
rem  Sobe a aplicacao com PostgreSQL embarcado e dados de exemplo e abre o
rem  navegador na tela de login (http://localhost:8080).
rem  Gerente: 111.444.777-35 / Gerente@Demo1   Vendedor: 529.982.247-25 / Vendedor@123
rem  Para encerrar: feche esta janela ou aperte Ctrl+C. Os dados sao apagados.
rem ============================================================================
setlocal EnableDelayedExpansion
title PDV Web - modo demonstracao
cd /d "%~dp0"

rem Procura um Java 25 ou mais novo: JAVA_HOME, JDK do IntelliJ, JDKs instalados, java do PATH
set "JDK="
call :testar "%JAVA_HOME%"
if not defined JDK for /d %%D in ("%ProgramFiles%\JetBrains\IntelliJ IDEA*") do if not defined JDK call :testar "%%~D\jbr"
if not defined JDK for /d %%D in ("%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Eclipse Adoptium\jdk-*" "%ProgramFiles%\Microsoft\jdk-*" "%USERPROFILE%\.jdks\*") do if not defined JDK call :testar "%%~D"
if not defined JDK for /f "delims=" %%J in ('where java 2^>nul') do if not defined JDK call :testar "%%~dpJ.."

if not defined JDK (
  echo.
  echo  Nao encontrei o Java 25. Instale o JDK 25 ^(https://adoptium.net^) ou defina JAVA_HOME.
  echo.
  pause
  exit /b 1
)
set "JAVA_HOME=%JDK%"
echo  Java: %JAVA_HOME%
echo  Iniciando o PDV Web... o navegador abre sozinho quando estiver pronto ^(30-60 s; na primeira vez, mais^).
echo.

call "%~dp0mvnw.cmd" -q spring-boot:test-run "-Dspring-boot.run.main-class=br.com.empresa.pdv.DemonstracaoPdvWebApi"
if errorlevel 1 (
  echo.
  echo  A aplicacao parou com erro. Se a mensagem fala da porta 8080, ja existe outra copia rodando: feche-a.
  pause
)
exit /b

rem --- :testar PASTA  -> define JDK se PASTA\bin\java.exe for versao 25 ou mais nova
:testar
set "CANDIDATO=%~1"
if "%CANDIDATO%"=="" exit /b
if not exist "%CANDIDATO%\bin\java.exe" exit /b
set "VERSAO="
"%CANDIDATO%\bin\java.exe" -version 2> "%TEMP%\pdv-java-versao.txt"
for /f "usebackq tokens=3" %%V in (`findstr /i "version" "%TEMP%\pdv-java-versao.txt"`) do set "VERSAO=%%~V"
for /f "delims=." %%M in ("!VERSAO!") do set "MAIOR=%%M"
if defined MAIOR if !MAIOR! GEQ 25 set "JDK=%CANDIDATO%"
set "MAIOR="
exit /b
