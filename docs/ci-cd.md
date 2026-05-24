# CI/CD — Pipeline Variables

## Variables (set in Azure DevOps UI → Pipelines → Variables)

| Variable           | Example Value                      | Secret |
|--------------------|------------------------------------|--------|
| ACR_NAME           | spawnbaseacrdev                    | No     |
| ACR_LOGIN_SERVER   | spawnbaseacrdev.azurecr.io         | No     |
| RESOURCE_GROUP     | spawnbase-rg                       | No     |
| AKS_CLUSTER_NAME   | spawnbase-aks-dev                  | No     |

## Service Connections (set in Project Settings → Service Connections)

| Name                        | Type              | Used In     |
|-----------------------------|-------------------|-------------|
| docker-registry-connection  | Docker Registry   | Push stage  |
| azure-subscription          | Azure Resource Mgr| CD pipeline |

## Why Not Store Secrets in the YAML?

Pipeline YAML is committed to Git — secrets in YAML
are exposed to everyone with repo access. Azure DevOps
pipeline variables marked as secret are:
- Encrypted at rest
- Masked in logs
- Not accessible to fork PRs
- Rotatable without changing code


## CD Pipeline Flow

```
CI completes on main
↓
CD triggered automatically
↓
Stage 1: Deploy to Dev
  az aks get-credentials
  kubectl set image (all 6 services)
  kubectl rollout status (waits for pods)
↓
Stage 2: Smoke Tests
  port-forward each service
  curl /actuator/health
  fail pipeline if any return non-200
↓
Stage 3: Manual Approval Gate ← human clicks Approve
↓
Stage 4: Deploy to Prod
  same kubectl set image pattern
  kubectl rollout status
```


## Rollback Strategy

Kubernetes keeps rollout history (default: 10 revisions).

Roll back one service:
```bash
kubectl rollout undo deployment/api-gateway \
  --namespace spawnbase
```

Roll back all services:
```bash
for svc in metadata-service lifecycle-service \
  provisioning-service credential-service \
  api-gateway spawnbase-ui; do
  kubectl rollout undo deployment/$svc \
    --namespace spawnbase
done
```

Check rollout history:
```bash
kubectl rollout history deployment/api-gateway \
  --namespace spawnbase
```

## Why `kubectl set image` and not re-apply manifests?

`kubectl apply -f manifests/` would apply all fields in
the YAML, overwriting any live config changes. `set image`
is surgical — it changes only the image tag and triggers
a rolling update. The rest of the deployment spec
(replicas, resource limits, probes) is untouched.