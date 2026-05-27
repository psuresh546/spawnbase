# SpawnBase

A self-hosted database provisioning platform. SpawnBase lets developers provision isolated database containers (PostgreSQL, MySQL, MongoDB) on demand via a REST API and React admin UI.

> ⚠️ Educational project. Not intended for production use.

---

## Related Repositories

| Repository | Description |
|---|---|
| [spawnbase](https://github.com/psuresh546/spawnbase) | Backend — Java/Spring Boot microservices |
| [spawnbase-ui](https://github.com/psuresh546/spawnbase-ui) | Frontend — React admin dashboard |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    React Admin UI                        │
│                   localhost:3000                         │
└───────────────────────┬─────────────────────────────────┘
                        │ JWT Bearer token
┌───────────────────────▼─────────────────────────────────┐
│                   API Gateway                            │
│              JWT auth + RBAC + routing                   │
│                   localhost:8080                         │
└──┬──────────┬──────────┬──────────┬──────────┬──────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
Metadata  Lifecycle Provisioning Credential  Admin
 :8081      :8082      :8083       :8084      :8081
JPA+PG     FSM      Docker API   AES enc    /admin/**
```

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT auth, RBAC, routing |
| metadata-service | 8081 | Instance state, JPA, events |
| lifecycle-service | 8082 | FSM state transitions |
| provisioning-service | 8083 | Docker Engine API |
| credential-service | 8084 | AES-encrypted credentials |
| spawnbase-ui | 3000 | React admin dashboard |

---

## Instance Lifecycle (FSM)

SpawnBase enforces a strict finite state machine with 9 states. Illegal transitions are rejected with a `400`.

```
REQUESTED → PROVISIONING → RUNNING
                         → STOPPED → STARTING    → RUNNING
                         → RESTARTING             → RUNNING
                         → DELETING               → DELETED (terminal)
                         → FAILED   (terminal)
                              ↑ drift detection (auto)
                              ↓ recover (manual) → REQUESTED
FAILED → DELETING → DELETED
```

See `docs/architecture.md` for design decisions.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 / 21 |
| Framework | Spring Boot 3.5.14 |
| Build | Maven multi-module |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA + Hibernate |
| Auth | JWT (jjwt 0.13.0) |
| Encryption | AES-256 |
| Containers | Docker Engine API (HTTP) |
| Frontend | React (CRA) |
| Observability | Prometheus + Grafana |
| IaC | Terraform (Azure) |
| Orchestration | Kubernetes (AKS) |
| CI/CD | Azure DevOps Pipelines |

---

## Key Design Patterns

### Finite State Machine

`LifecycleStateMachine` enforces valid transitions. Illegal transitions throw `InvalidTransitionException`.

### Reconciliation Loop (Drift Detector)

`DriftDetector` runs every 60 seconds comparing SpawnBase metadata with actual Docker container state:

- `RUNNING` in SpawnBase + container `GONE` → mark `FAILED`
- `FAILED` in SpawnBase + container `OK` → mark `RUNNING`
- `DELETED` in SpawnBase + container found → remove orphan

### Rollback Pattern

When provisioning fails at any step, rollback removes any partial container and marks the instance `FAILED`. No orphaned containers.

### Strategy Pattern

`DatabaseProvider` interface with three implementations: `PostgreSQLProvider`, `MySQLProvider`, `MongoDBProvider`. Adding a new database type requires one new class.

### Event Log

Every state change is recorded with timestamp, previous state, new state, and trigger. Queryable via `GET /api/instances/{id}/events`.

---

## Running Locally

### Prerequisites

- Docker Desktop (TCP enabled on port 2375)
- Java 17+
- Maven 3.9+
- Node 20+

### Start the stack

```bash
git clone https://github.com/psuresh546/spawnbase.git
git clone https://github.com/psuresh546/spawnbase-ui.git

cd spawnbase
docker-compose build
docker-compose up -d
```

Services start on ports 8080–8084. UI at http://localhost:3000.

### With observability (Grafana + Prometheus)

```bash
docker-compose \
  -f docker-compose.yml \
  -f infra/grafana/docker-compose.grafana.yml \
  up -d
```

Grafana at http://localhost:3001 (admin / admin).

### Run tests

```bash
mvn test
# 63 tests, 0 failures
```

---

## API Quick Reference

### Get a token

```bash
POST /api/auth/token
{"userId": "admin-user", "role": "ADMIN"}
→ {"token": "eyJ..."}
```

### Provision a database

```bash
# 1. Create instance
POST /api/instances
{"name":"my-db","dbType":"POSTGRESQL","ownerId":"user-1"}

# 2. Transition to PROVISIONING
POST /api/lifecycle/instances/{id}/transition
{"targetState":"PROVISIONING"}

# 3. Provision (starts real container)
POST /api/provisioning/instances/{id}/provision
{"dbType":"POSTGRESQL"}

# 4. Poll until RUNNING
GET /api/instances/{id}
→ {"state":"RUNNING","hostPort":32768}

# 5. Get credentials
GET /api/credentials/{id}
→ {"username":"spawnbase","password":"...","connectionUrl":"..."}
```

---

## Infrastructure

```
infra/
├── terraform/     Azure: AKS, ACR, PostgreSQL, Key Vault
├── k8s/           Kubernetes manifests (10 files)
└── grafana/       Prometheus + Grafana dashboard
```

- Terraform validated locally with `terraform validate`
- K8s manifests validated with `kubectl apply --dry-run=client`

---

## CI/CD

| File | Purpose |
|---|---|
| `azure-pipelines.yml` | CI: test → build → push → validate |
| `azure-pipelines-cd.yml` | CD: deploy dev → smoke test → prod gate |

---

## Chaos Tests

| Scenario | Detection | Resolution |
|---|---|---|
| Docker killed mid-provision | Immediate | Rollback → `FAILED` |
| Container externally killed | ≤60s drift cycle | Auto → `FAILED` |
| Container self-healed | ≤60s drift cycle | Auto → `RUNNING` |

See `docs/chaos-test-results.md` for full test results.

---

## Test Coverage

| Module | Tests | Type |
|---|---|---|
| LifecycleStateMachine | 13 | Unit |
| LifecycleService | 3 | Unit |
| InstanceService | 5 | Unit |
| InstanceController | 11 | Integration |
| InstanceRepository | 7 | Integration |
| StateMachine integration | 7 | Integration |
| Application context | 4 | Smoke |
| **Total** | **63** | |

---

## Project Structure

```
spawnbase/
├── common/                  InstanceState, DatabaseType, events
├── api-gateway/             JWT filter, CORS, routing
├── metadata-service/        JPA entities, repositories, events
├── lifecycle-service/       FSM, transition controller
├── provisioning-service/    Docker client, drift detector
├── credential-service/      AES encryption, credential store
├── docker-compose.yml       Full local stack
├── azure-pipelines.yml      CI pipeline
├── azure-pipelines-cd.yml   CD pipeline
├── infra/terraform/         Azure infrastructure (validated)
├── infra/k8s/               Kubernetes manifests (validated)
├── infra/grafana/           Observability stack
└── docs/                    Architecture decisions + chaos tests
```

---

## Future Scope

- **Stop / Start / Restart / Delete operations** — FSM states exist; Docker API calls need implementing in `provisioning-service`
- **Replace Docker socket with Kubernetes Jobs** — removes the need for privileged host access in K8s
- **Multi-node support** — currently single Docker host; distributing containers across nodes requires a scheduler layer
- **Storage persistence** — provisioned containers use ephemeral storage; attaching persistent volumes would survive container restarts
- **Custom resource limits** — allow users to specify CPU and memory limits per instance
- **Database version selection** — currently pinned to `postgres:15`, `mysql:8.0`, `mongo:7.0`; expose version as a provisioning parameter
- **Real auth provider** — replace the dev token endpoint with OAuth2 / Azure AD / Auth0
- **Backup and restore** — scheduled `pg_dump` for PostgreSQL instances
- **Multi-tenancy** — namespace isolation between owners