# ─────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy parent POM
COPY pom.xml .

# Copy ALL module POMs so parent POM resolves
# Maven multi-module requires all declared modules
# to exist even when building only one
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY lifecycle-service/pom.xml lifecycle-service/pom.xml
COPY provisioning-service/pom.xml provisioning-service/pom.xml
COPY credential-service/pom.xml credential-service/pom.xml

# Copy source only for modules we actually need to build
COPY common/src common/src
COPY metadata-service/pom.xml metadata-service/pom.xml
COPY metadata-service/src metadata-service/src

# Build only metadata-service and its dependencies
RUN mvn clean package -pl metadata-service -am \
    -DskipTests --no-transfer-progress

# ─────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN addgroup --system spawnbase && \
    adduser --system --ingroup spawnbase spawnbase

COPY --from=builder \
    /build/metadata-service/target/metadata-service-*.jar \
    app.jar

RUN chown spawnbase:spawnbase app.jar

USER spawnbase

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]