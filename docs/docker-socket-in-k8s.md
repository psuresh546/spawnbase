# Docker Socket in Kubernetes — SpawnBase Architecture Decision

## Context

SpawnBase's `provisioning-service` creates and manages
Docker containers on behalf of users. It calls the Docker
Engine API directly to pull images, run containers, and
inspect container health.

## The Problem in Kubernetes

In Docker Compose, this is simple:

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

In Kubernetes, the same pattern uses a `hostPath` volume:

```yaml
volumes:
  - name: docker-socket
    hostPath:
      path: /var/run/docker.sock
      type: Socket
```

## Security Trade-Off

Mounting the Docker socket grants the container
**root-equivalent access to the host node**.

A compromised provisioning-service pod could:
- Read all containers on the host
- Start privileged containers
- Mount host filesystems
- Escape the pod sandbox

## Why We Accept This Trade-Off

SpawnBase accepts this risk because:

1. **Internal service only** — provisioning-service
   is ClusterIP (never exposed externally). All access
   is routed through api-gateway which enforces JWT
   authentication and RBAC.

2. **Architectural requirement** — the core value of
   SpawnBase is provisioning real containers. There
   is no way to do this without Docker access.

3. **Single replica** — only one pod accesses the
   socket, minimising blast radius.

4. **Non-root where possible** — the pod runs with
   `allowPrivilegeEscalation: false`. Root access is
   only required for the socket itself.

## Production Alternatives

### Option 1 — Docker-in-Docker (DinD)
Run a Docker daemon as a sidecar container inside the pod.
The provisioning-service connects to the sidecar daemon,
not the host daemon.

**Pro:** Host daemon is fully isolated.
**Con:** Privileged container still required for DinD.
Nested virtualisation — slower image pulls.

### Option 2 — Dedicated Provisioning Node Pool
Run provisioning-service on a dedicated node pool
with a taint, so no other workloads share the node.

```yaml
tolerations:
  - key: "docker-access"
    operator: "Equal"
    value: "true"
    effect: "NoSchedule"
```

**Pro:** Blast radius limited to dedicated nodes.
**Con:** Higher cost — dedicated nodes sit idle.

### Option 3 — Replace Docker with Kubernetes Jobs
Instead of calling Docker Engine API directly,
provisioning-service creates Kubernetes Jobs that
run the database containers as pods.

**Pro:** No Docker socket needed. Native K8s.
**Con:** Major refactor. Pod networking is different
from Docker networking — port mapping changes.
Adds K8s API server as a dependency.

### Option 4 — Kata Containers / gVisor
Use a container runtime that provides stronger isolation
(gVisor, Kata Containers) so Docker socket access is
less dangerous.

**Pro:** Stronger sandbox — socket compromise can't
reach the real host kernel.
**Con:** Requires cluster-level runtime configuration.
Not available on all managed K8s services.

## Current Decision

SpawnBase uses the direct Docker socket mount.
This is appropriate for:
- A learning/portfolio project
- Internal developer tooling
- Environments where the provisioning-service
  is trusted and access-controlled

Before going to production at scale, migrate to
Option 3 (Kubernetes Jobs) to eliminate the
socket dependency entirely.

## Interview Talking Points

> "We mount the Docker socket in provisioning-service
> because SpawnBase's core feature is provisioning
> real database containers. It's a deliberate trade-off
> — we mitigate the risk by keeping the service internal
> (ClusterIP only), running a single replica, and
> disabling privilege escalation where possible.
> The production migration path would be replacing
> the Docker Engine API calls with Kubernetes Job
> creation, which eliminates the socket dependency
> entirely and is the pattern used by tools like
> Helm and the Kubernetes operator framework."