# ─────────────────────────────────────────
# Azure Key Vault
# ─────────────────────────────────────────
# Stores sensitive secrets:
# - postgres-password   → metadata-service DB password
# - jwt-secret         → api-gateway JWT signing key
# - encryption-key     → credential-service AES key
#
# Secrets are injected into pods at runtime
# via the CSI Secrets Store driver
# (Day 29: Workload Identity)
# ─────────────────────────────────────────

data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "spawnbase" {
  name                = "spawnbase-kv-${var.environment}"
  location            = azurerm_resource_group.spawnbase.location
  resource_group_name = azurerm_resource_group.spawnbase.name
  tenant_id           = data.azurerm_client_config.current.tenant_id

  sku_name = "standard"

  # Soft delete — secrets recoverable for 7 days
  soft_delete_retention_days = 7

  # RBAC authorization model
  # (replaces legacy access policies)
  enable_rbac_authorization = true

  tags = local.common_tags
}

# ─────────────────────────────────────────
# Key Vault Secrets
# ─────────────────────────────────────────
resource "azurerm_key_vault_secret" "postgres_password" {
  name         = "postgres-password"
  value        = var.postgres_admin_password
  key_vault_id = azurerm_key_vault.spawnbase.id

  # Depends on identity having write access
  depends_on = [azurerm_role_assignment.terraform_kv_admin]
}

resource "azurerm_key_vault_secret" "jwt_secret" {
  name         = "jwt-secret"
  value        = var.jwt_secret
  key_vault_id = azurerm_key_vault.spawnbase.id

  depends_on = [azurerm_role_assignment.terraform_kv_admin]
}

# ─────────────────────────────────────────
# Role Assignments
# ─────────────────────────────────────────

# Terraform runner needs admin to create secrets
resource "azurerm_role_assignment" "terraform_kv_admin" {
  scope                = azurerm_key_vault.spawnbase.id
  role_definition_name = "Key Vault Administrator"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Managed identity reads secrets at runtime
resource "azurerm_role_assignment" "workload_identity_kv_reader" {
  scope                = azurerm_key_vault.spawnbase.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.spawnbase.principal_id
}