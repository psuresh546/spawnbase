# Workload Identity — SpawnBase Key Vault Integration

## Problem

Services need secrets (DB password, JWT key, AES key).

Bad patterns:
- Hardcoded in `application.properties` → repo exposure
- Stored in K8s Secrets → base64, not encrypted at rest
- Passed as env vars in compose → visible in `docker inspect`

## Solution — Azure Workload Identity

Workload Identity eliminates credentials entirely. Pods prove their identity via a Kubernetes Service Account token. Azure AD exchanges that token for an access token. The pod reads Key Vault secrets without handling any password.

## Flow

```
Pod starts
→ Presents K8s Service Account token to Azure AD
→ Azure AD verifies token via OIDC issuer URL
→ Azure AD issues access token for Managed Identity
→ CSI driver uses token to read Key Vault secrets
→ Secrets mounted as files at /mnt/secrets/
→ Secrets also synced to K8s Secret object
→ Spring Boot reads secrets via secretKeyRef env vars
→ Pod starts with correct credentials
```

## Components

### 1. AKS OIDC Issuer (Terraform — `aks.tf`)

```hcl
oidc_issuer_enabled       = true
workload_identity_enabled = true
```

AKS exposes an OIDC endpoint that Azure AD uses to verify Service Account tokens.

### 2. User-Assigned Managed Identity (Terraform — `identity.tf`)

```hcl
resource "azurerm_user_assigned_identity" "spawnbase" {
  name = "spawnbase-identity-dev"
}
```

The Azure identity that pods impersonate. Has Key Vault Secrets User role assigned.

### 3. Federated Identity Credential (Terraform — `identity.tf`)

```hcl
resource "azurerm_federated_identity_credential" "spawnbase" {
  issuer  = azurerm_kubernetes_cluster.spawnbase.oidc_issuer_url
  subject = "system:serviceaccount:spawnbase:spawnbase-sa"
}
```

Links the K8s Service Account to the Managed Identity. This is the trust relationship — only this specific SA can impersonate this identity.

### 4. K8s Service Account (K8s — `service-account.yaml`)

```yaml
annotations:
  azure.workload.identity/client-id: "MANAGED_IDENTITY_CLIENT_ID"
labels:
  azure.workload.identity/use: "true"
```

The SA annotation links it to the Azure MI.

### 5. SecretProviderClass (K8s — `secrets-store.yaml`)

Declares which Key Vault secrets to fetch and how to sync them to K8s Secret objects.

### 6. CSI Volume in Pod

Triggers secret fetch on pod startup. Without this volume mount, no secrets are fetched even if the SA has correct permissions.

## Secret Rotation

Key Vault secrets can be rotated without redeploying pods:

- Update secret value in Key Vault
- CSI driver polls every 2 minutes (default)
- K8s Secret is updated automatically
- Pod reads new value on next restart OR via file watch

For zero-downtime rotation, applications should watch the mounted file at `/mnt/secrets/postgres-password` rather than reading the env var (which only updates on pod restart).

## Why Not K8s Secrets?

|                      | K8s Secrets         | Workload Identity          |
|----------------------|---------------------|----------------------------|
| Encrypted at rest    | Only with etcd encryption | Yes, in Key Vault     |
| Audit log            | No                  | Yes, Key Vault access log  |
| Rotation             | Manual redeploy     | Automatic                  |
| Credentials needed   | Yes (base64 value)  | No credentials             |
| RBAC                 | K8s RBAC only       | Azure RBAC + K8s RBAC      |

## Setup Commands (when Azure subscription available)

```bash
# 1. Install CSI driver
helm repo add csi-secrets-store \
  https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm install csi-secrets-store \
  csi-secrets-store/secrets-store-csi-driver \
  --namespace kube-system --set syncSecret.enabled=true

# 2. Install Azure provider
helm repo add azure-csi \
  https://azure.github.io/secrets-store-csi-driver-provider-azure/charts
helm install azure-csi \
  azure-csi/csi-secrets-store-provider-azure \
  --namespace kube-system

# 3. Get values from Terraform outputs
TENANT_ID=$(az account show --query tenantId -o tsv)
CLIENT_ID=$(terraform -chdir=infra/terraform output \
  -raw managed_identity_client_id)

# 4. Update secrets-store.yaml with real values
# 5. Apply manifests
kubectl apply -f infra/k8s/

# 6. Verify secret is mounted
kubectl exec -n spawnbase \
  deploy/metadata-service -- \
  cat /mnt/secrets/postgres-password
```