FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY metadata-service/pom.xml metadata-service/pom.xml
COPY lifecycle-service/pom.xml lifecycle-service/pom.xml
COPY provisioning-service/pom.xml provisioning-service/pom.xml
COPY credential-service/pom.xml credential-service/pom.xml

COPY common/src common/src
COPY api-gateway/src api-gateway/src

RUN mvn clean package -pl api-gateway -am \
    -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN addgroup --system spawnbase && \
    adduser --system --ingroup spawnbase spawnbase

COPY --from=builder \
    /build/api-gateway/target/api-gateway-*.jar \
    app.jar

RUN chown spawnbase:spawnbase app.jar

USER spawnbase

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]