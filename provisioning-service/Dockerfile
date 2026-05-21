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
COPY provisioning-service/src provisioning-service/src

RUN mvn clean package -pl provisioning-service -am \
    -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN addgroup --system spawnbase && \
    adduser --system --ingroup spawnbase spawnbase

COPY --from=builder \
    /build/provisioning-service/target/provisioning-service-*.jar \
    app.jar

RUN chown spawnbase:spawnbase app.jar

USER root

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]