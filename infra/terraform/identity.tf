# ─────────────────────────────────────────
# User-Assigned Managed Identity
# ─────────────────────────────────────────
# Used by SpawnBase pods to authenticate
# to Azure services (Key Vault, ACR)
# without storing credentials in pods.
#
# Workload Identity flow:
# Pod → Service Account → Managed Identity
#     → Key Vault (read secrets)
#
# This replaces the old pattern of:
# - Mounting service principal credentials
# - Storing connection strings in Secrets
# - Using pod-level environment variables
# ─────────────────────────────────────────

resource "azurerm_user_assigned_identity" "spawnbase" {
  name                = "spawnbase-identity-${var.environment}"
  location            = azurerm_resource_group.spawnbase.location
  resource_group_name = azurerm_resource_group.spawnbase.name

  tags = local.common_tags
}

# ─────────────────────────────────────────
# Federated Identity Credential
# ─────────────────────────────────────────
# Links the Managed Identity to the
# Kubernetes Service Account.
# This is the core of Workload Identity —
# pods presenting the SA token get
# exchanged for an Azure AD token.
resource "azurerm_federated_identity_credential" "spawnbase" {
  name                = "spawnbase-federated"
  resource_group_name = azurerm_resource_group.spawnbase.name
  audience            = ["api://AzureADTokenExchange"]
  issuer              = azurerm_kubernetes_cluster.spawnbase.oidc_issuer_url
  parent_id           = azurerm_user_assigned_identity.spawnbase.id

  # The K8s service account that gets this identity
  subject = "system:serviceaccount:spawnbase:spawnbase-sa"
}