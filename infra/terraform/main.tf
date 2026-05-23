# ─────────────────────────────────────────
# SpawnBase — Azure Infrastructure
# ─────────────────────────────────────────
# Provisions all Azure resources needed
# to run SpawnBase on AKS.
#
# Resources:
# - Resource Group
# - AKS Cluster
# - Azure Container Registry (ACR)
# - Azure Database for PostgreSQL Flexible Server
# - Key Vault
# - Managed Identity
# ─────────────────────────────────────────

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.100"
    }
  }

  # In production: use Azure Blob Storage as backend
  # so state is shared across the team.
  # Uncomment when deploying for real:
  #
  # backend "azurerm" {
  #   resource_group_name  = "spawnbase-tf-state-rg"
  #   storage_account_name = "spawnbasetfstate"
  #   container_name       = "tfstate"
  #   key                  = "spawnbase.tfstate"
  # }
}

provider "azurerm" {
  features {
    key_vault {
      # Soft delete is on by default in Azure.
      # This allows Terraform to purge on destroy
      # during development — disable in production.
      purge_soft_delete_on_destroy = true
    }
  }
}

# ─────────────────────────────────────────
# Resource Group
# ─────────────────────────────────────────
# All SpawnBase resources live in one RG
# for easy management and cost tracking.
resource "azurerm_resource_group" "spawnbase" {
  name     = var.resource_group_name
  location = var.location

  tags = local.common_tags
}

# ─────────────────────────────────────────
# Locals
# ─────────────────────────────────────────
locals {
  common_tags = {
    project     = "spawnbase"
    environment = var.environment
    managed_by  = "terraform"
  }
}