# ─────────────────────────────────────────
# AKS Cluster
# ─────────────────────────────────────────
# Hosts all 6 SpawnBase microservices
# as Kubernetes Deployments.
#
# Design decisions:
# - System node pool: reserved for K8s system pods
# - RBAC enabled: workload identity requires it
# - Network plugin: kubenet (simpler for dev)
# - Upgrade channel: patch (auto patch upgrades)
# ─────────────────────────────────────────

resource "azurerm_kubernetes_cluster" "spawnbase" {
  name                = "spawnbase-aks-${var.environment}"
  location            = azurerm_resource_group.spawnbase.location
  resource_group_name = azurerm_resource_group.spawnbase.name
  dns_prefix          = "spawnbase-${var.environment}"

  # Kubernetes version — use latest stable
  # kubernetes_version = "1.29"

  default_node_pool {
    name       = "system"
    node_count = var.aks_node_count
    vm_size    = var.aks_node_vm_size

    # OS disk for node VMs
    os_disk_size_gb = 50

    # Labels for system workloads
    node_labels = {
      "nodepool" = "system"
    }
  }

  # Managed identity for the cluster itself
  # (allows AKS to pull from ACR)
  identity {
    type = "SystemAssigned"
  }

  # OIDC issuer — required for Workload Identity
  # (Day 29: Key Vault secret injection)
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  # Automatic patch upgrades
  automatic_channel_upgrade = "patch" # automatic_upgrade_channel = "patch" -- {Terraform v1.15.4}

  # Azure Monitor integration
  monitor_metrics {}

  tags = local.common_tags
}

# ─────────────────────────────────────────
# AcrPull Role Assignment
# ─────────────────────────────────────────
# Grants AKS permission to pull images
# from ACR without storing credentials.
#
# This is the correct pattern:
# AKS managed identity → AcrPull role → ACR
# Not: docker login with username/password
resource "azurerm_role_assignment" "aks_acr_pull" {
  scope                = azurerm_container_registry.spawnbase.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_kubernetes_cluster.spawnbase.kubelet_identity[0].object_id # getting error on object_id (unresolved object reference)
}