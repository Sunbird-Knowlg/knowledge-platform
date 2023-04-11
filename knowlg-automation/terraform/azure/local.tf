
locals {
    common_tags = {
      Environment = "${var.env}"
      BuildingBlock = "${var.building_block}"
    }
    subid = split("-", "${data.azurerm_subscription.current.subscription_id}")
    environment_name = "${var.building_block}-${var.env}"
    uid = local.subid[0]
    env_name_without_dashes = replace(local.environment_name, "-", "")
    storage_account_name = "${local.env_name_without_dashes}${local.uid}"

    storage = {
      azure_storage_account_name = azurerm_storage_account.storage_account.name
      azure_storage_account_key  = azurerm_storage_account.storage_account.primary_access_key
      azure_storage_container    = azurerm_storage_container.storage_container.name
    }
}