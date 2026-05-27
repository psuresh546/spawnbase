# SpawnBase — Architecture Decision Records

## ADR-001: Multi-module Maven over separate repos

**Decision:** Single multi-module Maven project.

**Reason:** Shared `common` module (`InstanceState`, `DatabaseType`, events) needs to be compiled together. Separate repos would require publishing common to a Maven registry — unnecessary complexity for this scale.

**Trade-off:** Docker builds are slower (must copy all module POMs even when building one service). Mitigated by Docker layer caching.

---

## ADR-002: Spring Cloud Gateway MVC over WebFlux

**Decision:** `spring-cloud-starter-gateway-server-webmvc` (servlet-based) instead of the reactive gateway.

**Reason:** All downstream services are servlet-based Spring Boot apps. Mixing a reactive gateway with servlet services adds complexity without benefit. The MVC gateway is simpler to configure and debug.

**Trade-off:** Lower throughput ceiling than reactive. Acceptable for a provisioning tool (not a high-throughput API).

---

## ADR-003: Docker Engine API over Kubernetes Jobs

**Decision:** Call Docker Engine HTTP API directly from provisioning-service.

**Reason:** Simpler to implement and test locally without a K8s cluster. Docker socket gives direct container control — create, start, inspect, stop, remove.

**Trade-off:** Requires Docker socket mount in K8s (root-equivalent host access). Production migration path: replace Docker API calls with `kubectl apply` Job manifests. The FSM and credential management are unaffected by this change.

---

## ADR-004: AES encryption in credential-service

**Decision:** AES-256 encryption for stored credentials using a service-level encryption key.

**Reason:** Credentials (DB passwords, connection strings) must not be stored in plaintext. AES-256 with a `SecureRandom`-generated password per instance provides strong protection.

**Trade-off:** The encryption key itself must be protected. In production, loaded from Azure Key Vault via Workload Identity (Day 29). In development, set via environment variable.

---

## ADR-005: In-process Spring Events over message broker

**Decision:** Spring `ApplicationEvent` for audit logging, not Kafka/RabbitMQ.

**Reason:** The audit log is a side effect of state changes, not a cross-service concern. In-process events participate in the same transaction as the state change — if the DB write fails, no event is published. A message broker would decouple this but add operational complexity.

**Trade-off:** Events are not durable across service restarts. Acceptable — the instance state table is the source of truth, not the event log.

---

## ADR-006: Reconciliation loop over webhooks

**Decision:** Poll Docker every 60 seconds (drift detector) instead of subscribing to Docker events.

**Reason:** Docker events are delivered over a streaming HTTP connection. If the connection drops, events are lost. Polling is less efficient but guaranteed correct — it reflects actual state at the time of the check.

**Trade-off:** Up to 60 seconds of stale metadata after an out-of-band container change. Acceptable for a provisioning tool — not a real-time system.

---

## ADR-007: Single repo for backend + separate for UI

**Decision:** Backend services in `spawnbase/`, React UI in `spawnbase-ui/`.

**Reason:** Different build systems (Maven vs npm), different deployment lifecycles, different Docker contexts. Keeping them separate avoids polluting the Maven multi-module structure with Node.js artefacts.

**Trade-off:** `docker-compose.yml` references `../spawnbase-ui` with a relative path — slightly awkward but functional.