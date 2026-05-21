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
COPY lifecycle-service/src lifecycle-service/src

# Step 1: Install parent POM to .m2 (non-recursive)
# Step 2: Install common to .m2
# Step 3: Package lifecycle-service (finds both in .m2)
RUN mvn install -N \
    -DskipTests --no-transfer-progress && \
    mvn clean install -pl common \
    -DskipTests --no-transfer-progress && \
    mvn clean package -pl lifecycle-service \
    -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN addgroup --system spawnbase && \
    adduser --system --ingroup spawnbase spawnbase

COPY --from=builder \
    /build/lifecycle-service/target/lifecycle-service-*.jar \
    app.jar

RUN chown spawnbase:spawnbase app.jar

USER spawnbase

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]