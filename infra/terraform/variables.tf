# ─────────────────────────────────────────
# Input Variables
# ─────────────────────────────────────────
# Override defaults in terraform.tfvars
# or via environment variables:
# export TF_VAR_location="eastus"
# ─────────────────────────────────────────

variable "resource_group_name" {
  description = "Name of the Azure Resource Group"
  type        = string
  default     = "spawnbase-rg"
}

variable "location" {
  description = "Azure region for all resources"
  type        = string
  default     = "eastus"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "aks_node_count" {
  description = "Number of AKS worker nodes"
  type        = number
  default     = 2
}

variable "aks_node_vm_size" {
  description = "VM size for AKS worker nodes"
  type        = string
  default     = "Standard_D2s_v3"
}

variable "postgres_sku" {
  description = "PostgreSQL Flexible Server SKU"
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_admin_password" {
  description = "PostgreSQL admin password"
  type        = string
  sensitive   = true
  # No default — must be provided in tfvars
  # or as environment variable
}

variable "jwt_secret" {
  description = "JWT signing secret for API Gateway"
  type        = string
  sensitive   = true
  # Stored in Key Vault — never in code
}