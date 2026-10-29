# Build context = raiz del repo (necesita el pom.xml del reactor)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY ms-markets/pom.xml ms-markets/pom.xml
COPY ms-clientes/pom.xml ms-clientes/pom.xml
COPY ms-pedidos/pom.xml ms-pedidos/pom.xml
COPY ms-inventory/pom.xml ms-inventory/pom.xml
COPY ms_auth/pom.xml ms_auth/pom.xml
RUN mvn -q -pl api-gateway -am dependency:go-offline

COPY api-gateway/src api-gateway/src
RUN mvn -q -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
