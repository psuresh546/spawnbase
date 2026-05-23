# ─────────────────────────────────────────
# Azure Container Registry
# ─────────────────────────────────────────
# Stores Docker images for all 6 services.
# AKS pulls from here via managed identity
# (no credentials needed).
#
# SKU comparison:
# Basic  — 10GB, no geo-replication, dev use
# Standard — 100GB, webhooks, staging use
# Premium — geo-replication, private link, prod
# ─────────────────────────────────────────

resource "azurerm_container_registry" "spawnbase" {
  name                = "spawnbaseacr${var.environment}"
  resource_group_name = azurerm_resource_group.spawnbase.name
  location            = azurerm_resource_group.spawnbase.location

  # Basic is sufficient for dev/learning
  # Use Standard or Premium for production
  sku = "Basic"

  # Admin account disabled — use managed identity
  # Enabling admin_enabled is a security anti-pattern
  admin_enabled = false

  tags = local.common_tags
}