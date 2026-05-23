# ─────────────────────────────────────────
# Output Values
# ─────────────────────────────────────────
# Used by CI/CD pipeline to configure
# kubectl and docker push after apply.
# ─────────────────────────────────────────

output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.spawnbase.name
}

output "aks_cluster_name" {
  description = "AKS cluster name"
  value       = azurerm_kubernetes_cluster.spawnbase.name
}

output "aks_cluster_id" {
  description = "AKS cluster resource ID"
  value       = azurerm_kubernetes_cluster.spawnbase.id
}

output "acr_login_server" {
  description = "ACR login server URL for docker push"
  value       = azurerm_container_registry.spawnbase.login_server
}

output "acr_name" {
  description = "ACR name"
  value       = azurerm_container_registry.spawnbase.name
}

output "postgres_fqdn" {
  description = "PostgreSQL server FQDN"
  value       = azurerm_postgresql_flexible_server.spawnbase.fqdn
}

output "key_vault_uri" {
  description = "Key Vault URI for secret access"
  value       = azurerm_key_vault.spawnbase.vault_uri
}

output "managed_identity_client_id" {
  description = "Managed Identity client ID for workload identity"
  value       = azurerm_user_assigned_identity.spawnbase.client_id
}

output "kubectl_command" {
  description = "Command to configure kubectl"
  value = "az aks get-credentials --resource-group ${azurerm_resource_group.spawnbase.name} --name ${azurerm_kubernetes_cluster.spawnbase.name}"
}