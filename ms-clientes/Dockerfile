# Build context = raiz del repo (necesita el pom.xml del reactor)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY ms-markets/pom.xml ms-markets/pom.xml
COPY ms-clientes/pom.xml ms-clientes/pom.xml
COPY ms-pedidos/pom.xml ms-pedidos/pom.xml
COPY ms-inventory/pom.xml ms-inventory/pom.xml
COPY ms-products/pom.xml ms-products/pom.xml
COPY ms_auth/pom.xml ms_auth/pom.xml
RUN mvn -q -pl ms-clientes -am dependency:go-offline

COPY ms-clientes/src ms-clientes/src
RUN mvn -q -pl ms-clientes -am -DskipTests package

# Separa el jar en capas de Spring Boot (dependencias, loader, snapshots, aplicacion)
RUN cp ms-clientes/target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# JRE minimo: modulos que detecta jdeps + los que se cargan por reflexion
RUN jdeps --ignore-missing-deps -q --recursive --multi-release 17 --print-module-deps \
        --class-path "$(find extracted -path '*/lib/*.jar' | tr '\n' ':')" \
        extracted/application/app.jar > modules.txt \
    && jlink --add-modules "$(cat modules.txt),jdk.crypto.ec,jdk.unsupported,jdk.management,jdk.zipfs,jdk.charsets,jdk.localedata,java.instrument,java.naming,java.security.jgss" \
        --include-locales=en,es \
        --strip-debug --no-man-pages --no-header-files --compress=2 \
        --output /jre

# Distroless: solo glibc + certificados, sin shell ni gestor de paquetes; corre como nonroot
FROM gcr.io/distroless/base-debian12:nonroot
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=build /jre ${JAVA_HOME}
WORKDIR /app
# De menos a mas cambiante: un cambio de codigo solo reconstruye la ultima capa
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
