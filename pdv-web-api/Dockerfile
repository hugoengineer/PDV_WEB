# ---------- Etapa 1: build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
# Remove CR caso o arquivo tenha vindo de um checkout Windows; garante permissão de execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- Etapa 2: execução ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system pdv && useradd --system --gid pdv pdv
COPY --from=build /app/target/pdv-web-api-*.jar /app/app.jar
USER pdv

ENV TZ=America/Sao_Paulo
EXPOSE 8080
# Saúde da aplicação: GET /actuator/health (usar no health check do balanceador/EC2)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
