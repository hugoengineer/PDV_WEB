# PDV Web - MODO DEMONSTRAÇÃO em contêiner: mesma aplicação do demo.cmd/demo.sh
# (PostgreSQL embarcado, dados de exemplo, credenciais fictícias). Os dados somem ao parar o contêiner.
# Não usar em produção: para isso, veja docker-compose.yml na raiz do projeto.

# ---------- Etapa 1: build (classes da aplicação + classes de teste, onde fica o modo demo) ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q test-compile dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/lib

# ---------- Etapa 2: execução ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
# o PostgreSQL embarcado não roda como root
RUN groupadd --system pdv && useradd --system --gid pdv --create-home pdv
COPY --from=build /app/target/classes classes
COPY --from=build /app/target/test-classes test-classes
COPY --from=build /app/target/lib lib
USER pdv
ENV TZ=America/Sao_Paulo PDV_DEMO_ABRIR_NAVEGADOR=false
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "classes:test-classes:lib/*", "br.com.empresa.pdv.DemonstracaoPdvWebApi"]
