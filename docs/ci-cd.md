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