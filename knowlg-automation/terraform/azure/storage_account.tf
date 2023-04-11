resource "azurerm_storage_account" "storage_account" {
  name                     = "${local.storage_account_name}"
  resource_group_name      = data.azurerm_resource_group.rg.name
  location                 = var.location
  account_tier             = var.azure_storage_tier
  account_replication_type = var.azure_storage_replication

  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}