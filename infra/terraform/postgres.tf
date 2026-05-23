# ─────────────────────────────────────────
# Azure Database for PostgreSQL
# Flexible Server
# ─────────────────────────────────────────
# Used by metadata-service as its
# persistent store for instance metadata.
#
# Flexible Server vs Single Server:
# Flexible Server is the current generation —
# it supports zone redundancy, custom
# maintenance windows, and stop/start.
# Single Server is deprecated.
# ─────────────────────────────────────────

resource "azurerm_postgresql_flexible_server" "spawnbase" {
  name                   = "spawnbase-postgres-${var.environment}"
  resource_group_name    = azurerm_resource_group.spawnbase.name
  location               = azurerm_resource_group.spawnbase.location
  version                = "15"
  administrator_login    = "spawnbaseadmin"
  administrator_password = var.postgres_admin_password

  # B_Standard_B1ms: 1 vCore, 2GB RAM
  # Cheapest tier — fine for dev
  sku_name = var.postgres_sku

  # Storage in MB
  storage_mb = 32768  # 32GB

  # Backup retention
  backup_retention_days = 7

  # High availability — disabled for dev
  # Enable in production:
  # high_availability { mode = "ZoneRedundant" }

  tags = local.common_tags
}

# ─────────────────────────────────────────
# SpawnBase Database
# ─────────────────────────────────────────
resource "azurerm_postgresql_flexible_server_database" "spawnbase" {
  name      = "spawnbase"
  server_id = azurerm_postgresql_flexible_server.spawnbase.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# ─────────────────────────────────────────
# Firewall — Allow Azure Services
# ─────────────────────────────────────────
# Allows AKS pods to connect to PostgreSQL.
# In production: use VNet integration instead.
resource "azurerm_postgresql_flexible_server_firewall_rule" "allow_azure" {
  name             = "AllowAzureServices"
  server_id        = azurerm_postgresql_flexible_server.spawnbase.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}