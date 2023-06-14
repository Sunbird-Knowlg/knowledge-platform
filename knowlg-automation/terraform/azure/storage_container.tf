resource "azurerm_storage_container" "storage_container" {
  name                  = "${local.environment_name}"
  storage_account_name  = azurerm_storage_account.storage_account.name
  container_access_type = "private"
}