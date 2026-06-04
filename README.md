# SpawnBase

**A self-hosted database provisioning platform built on Java microservices and Docker**

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![Auth](https://img.shields.io/badge/Auth-JWT%20%2B%20RBAC-blue)](https://jwt.io/)
[![Persistence](https://img.shields.io/badge/Persistence-PostgreSQL%2015-blue)](https://www.postgresql.org/)
[![Orchestration](https://img.shields.io/badge/Orchestration-Docker%20%2B%20Kubernetes-informational)](https://kubernetes.io/)

> ⚠️ Educational project. Not intended for production use.

SpawnBase lets developers provision isolated PostgreSQL, MySQL, and MongoDB containers on demand via a REST API and a React admin dashboard. The project covers microservice design, JWT auth, FSM-based lifecycle management, Docker Engine API integration, AES-encrypted credential storage, and a full Azure DevOps CI/CD pipeline.

For architecture decisions and chaos test notes, see [`docs/`](docs/).

## Table of Contents

- [Why SpawnBase](#why-spawnbase)
- [Related Repositories](#related-repositories)
- [Architecture](#architecture)
- [Services](#services)
- [Instance Lifecycle (FSM)](#instance-lifecycle-fsm)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Quick Reference](#api-quick-reference)
- [Instance Operations](#instance-operations)
- [Infrastructure](#infrastructure)
- [CI/CD](#cicd)
- [Test Coverage](#test-coverage)
- [Project Structure](#project-structure)
- [Future Scope](#future-scope)

---

## Why SpawnBase

Most backend portfolio projects stop at CRUD over HTTP with a single database. SpawnBase goes several layers deeper:

- JWT-authenticated API gateway sitting in front of five independent microservices
- Finite state machine enforcing valid lifecycle transitions on every database instance
- Live Docker container provisioning via the Docker Engine HTTP API
- AES-256 encrypted credential storage isolated to its own service
- Grafana and Prometheus observability baked in from the start
- Validated Terraform and Kubernetes manifests targeting Azure AKS
- Full CI/CD pipeline with a manual approval gate before production deployment

That makes it a realistic reference for how a provisioning platform is stitched together end to end.

---

## Related Repositories

| Repository | Description |
|------------|-------------|
| [spawnbase](https://github.com/psuresh546/spawnbase) | Backend — Java/Spring Boot microservices |
| [spawnbase-ui](https://github.com/psuresh546/spawnbase-ui) | Frontend — React admin dashboard |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    React Admin UI                       │
│                   localhost:3000                        │
└───────────────────────┬─────────────────────────────────┘
                        │ JWT Bearer token
┌───────────────────────▼─────────────────────────────────┐
│                   API Gateway                           │
│              JWT auth + RBAC + routing                  │
│                   localhost:8080                        │
└──┬──────────┬──────────┬──────────┬──────────┬──────────┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
Metadata  Lifecycle Provisioning Credential  Admin
 :8081      :8082      :8083       :8084      :8081
JPA+PG     FSM       Docker API   AES enc   /admin/**
```

### Architecture Breakdown

#### `api-gateway`

Entry point for all client traffic. Validates JWT tokens, enforces RBAC, and routes requests to the appropriate downstream service. CORS configuration lives here.

#### `metadata-service`

Owns the canonical state of every database instance. Persists instance records and state history via Spring Data JPA to a shared PostgreSQL database. Publishes internal events on state changes.

#### `lifecycle-service`

Enforces the FSM. Accepts transition requests, validates them against the current state, and applies the new state. Rejects invalid transitions so provisioning logic can trust the state it reads.

#### `provisioning-service`

Communicates directly with the Docker Engine HTTP API. Starts, stops, restarts, and removes real database containers. Also runs a drift detector to reconcile expected state against what Docker actually reports.

#### `credential-service`

Generates and stores credentials for provisioned containers using AES-256 encryption. Returns decrypted credentials only to authenticated callers. No plaintext is persisted.

---

## Services

| Service | Port | Responsibility |
|---------|------|----------------|
| api-gateway | 8080 | JWT auth, RBAC, routing |
| metadata-service | 8081 | Instance state, JPA, events |
| lifecycle-service | 8082 | FSM state transitions |
| provisioning-service | 8083 | Docker Engine API |
| credential-service | 8084 | AES-encrypted credentials |
| spawnbase-ui | 3000 | React admin dashboard |

---

## Instance Lifecycle (FSM)

SpawnBase enforces a finite state machine with 9 states. Only valid transitions are accepted; invalid ones are rejected by the lifecycle service before any Docker call is made.

```
REQUESTED → PROVISIONING → RUNNING
                         → STOPPED → STARTING    → RUNNING
                                   → RESTARTING  → RUNNING
                                   → DELETING    → DELETED  (terminal)
                         → FAILED  (terminal)
                                   → recover     → REQUESTED
                                   → DELETING    → DELETED
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Build | Maven multi-module |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA + Hibernate |
| Auth | JWT (jjwt 0.12.6) |
| Encryption | AES-256 |
| Container Engine | Docker Engine HTTP API |
| Frontend | React 18 (CRA) |
| IaC | Terraform (Azure) |
| Orchestration | Kubernetes (AKS) |
| CI/CD | Azure DevOps Pipelines |

---

## Getting Started

### Prerequisites

- Docker Desktop (TCP enabled on port 2375)
- Java 17+
- Maven 3.9+
- Node 20+
- `psql` / `mysql` / `mongosh` (optional, for connecting to provisioned databases)

### Clone both repos

```bash
git clone https://github.com/psuresh546/spawnbase.git
git clone https://github.com/psuresh546/spawnbase-ui.git
```

Both must sit as sibling directories:

```
Desktop/
├── spawnbase/
└── spawnbase-ui/
```

### Enable Docker TCP

In Docker Desktop → Settings → General → enable **"Expose daemon on tcp://localhost:2375 without TLS"**

### Start the stack

```bash
cd spawnbase
docker-compose build
docker-compose up -d
```

Wait ~60 seconds for all services to boot.

- UI: http://localhost:3000
- API: http://localhost:8080
- Default login: `admin-user` / `ADMIN`

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

### Connect to provisioned databases

**PostgreSQL**
```bash
psql -h localhost -p <hostPort> -U <username> -d <dbName>
```

**MySQL**
```bash
mysql -h 127.0.0.1 -P <hostPort> -u <username> -p<password> <dbName>
```

**MongoDB**
```bash
mongosh "mongodb://<username>:<url-encoded-password>@localhost:<hostPort>/<dbName>"
# URL-encode special characters in password: @ → %40, # → %23
```

---

## Instance Operations

| Operation | Endpoint | Description |
|-----------|----------|-------------|
| Create | `POST /api/instances` | Create instance record |
| Provision | `POST /api/provisioning/instances/{id}/provision` | Start container |
| Stop | `POST /api/provisioning/instances/{id}/stop` | Stop container |
| Start | `POST /api/provisioning/instances/{id}/start` | Start stopped container |
| Restart | `POST /api/provisioning/instances/{id}/restart` | Restart container |
| Delete | `DELETE /api/provisioning/instances/{id}` | Remove container |
| Recover | `POST /api/instances/{id}/recover` | Reset FAILED → REQUESTED |

---

## Infrastructure

Infrastructure is fully validated; no cloud account is required to verify it.

```
infra/
├── terraform/     Azure: AKS, ACR, PostgreSQL, Key Vault
├── k8s/           Kubernetes manifests (validated with kubectl dry-run)
└── grafana/       Prometheus + Grafana observability stack
```

```bash
# Validate Terraform
terraform -chdir=infra/terraform validate

# Validate Kubernetes manifests
kubectl apply --dry-run=client -f infra/k8s/
```

---

## CI/CD

```
azure-pipelines.yml     CI: test → build → push → validate
azure-pipelines-cd.yml  CD: deploy dev → smoke test → manual approval → prod
```

---

## Test Coverage

| Module | Tests | Type |
|--------|-------|------|
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

### Directory Guide

#### `common/`

Shared types used across all services: `InstanceState` enum, `DatabaseType` enum, and internal event definitions.

#### `api-gateway/`

JWT validation filter, CORS configuration, and Spring Cloud Gateway routing rules.

#### `metadata-service/`

JPA entities, Spring Data repositories, and state-change event publishers. The single source of truth for instance state.

#### `lifecycle-service/`

FSM implementation and the transition controller. Validates all state changes before they reach metadata or provisioning.

#### `provisioning-service/`

Docker Engine HTTP API client, container lifecycle management, and a drift detector that reconciles expected versus actual container state.

#### `credential-service/`

AES-256 encryption utilities and the credential store. Keeps secrets isolated from other services.

#### `infra/`

Terraform modules for Azure (AKS, ACR, PostgreSQL, Key Vault), Kubernetes manifests, and the Grafana + Prometheus observability stack.

#### `docs/`

Architecture decision records and chaos test scenarios.

---

## Future Scope

- **Kafka/RabbitMQ** — Replace `CompletableFuture` with durable async messaging for provisioning events
- **Stop/Start/Restart/Delete** — FSM states exist; Docker API calls implemented but UI actions need full lifecycle wiring
- **Real auth provider** — Replace dev token endpoint with OAuth2/Azure AD/Auth0
- **User management** — Signup, admin approval, RBAC per user
- **Storage persistence** — Attach persistent volumes to provisioned containers
- **Database version selection** — Expose image tag as a provisioning parameter
- **Multi-node** — Distribute containers across multiple Docker hosts
- **Backup and restore** — Scheduled `pg_dump`/`mysqldump` for provisioned instances
- **Real-time updates** — WebSocket instead of polling
- **Kubernetes Jobs** — Replace Docker socket with K8s Job provisioning
- **Redis caching** — Cache instance metadata for faster dashboard loads

---

## License

This project is shared for educational and portfolio purposes.

No formal open-source license file is included, so the repository should be treated as all rights reserved until a license is added.
