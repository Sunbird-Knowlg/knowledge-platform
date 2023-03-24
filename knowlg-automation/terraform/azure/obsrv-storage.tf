resource "azurerm_storage_account" "obsrv-sa" {
  name                     = var.STORAGE_ACCOUNT 
  resource_group_name      = data.azurerm_resource_group.rg.name
  location                 = data.azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "GRS"

  tags = {
    environment = "dev"
  }
}

resource "azurerm_storage_container" "obsrv-container" {
  name                  = var.DRUID_DEEP_STORAGE_CONTAINER 
  storage_account_name  = azurerm_storage_account.obsrv-sa.name
  container_access_type = "container"
}



resource "azurerm_storage_blob" "obsrv-blob" {
  name                   = var.NAME_INGESTION_SPEC 
  storage_account_name   = azurerm_storage_account.obsrv-sa.name
  storage_container_name = azurerm_storage_container.obsrv-container.name
  type                   = "Block"
  source                 = var.PATH_INGESTION_SPEC
}

#resource "azurerm_storage_blob" "wiki-blob" {
#  name                   = "wikiticker-2015-09-12-sampled.json.gz"
#  storage_account_name   = azurerm_storage_account.obsrv-sa.name
#  storage_container_name = azurerm_storage_container.obsrv-container.name
#  type                   = "Block"
#  source                 = "../wikiticker-2015-09-12-sampled.json.gz"
#}
