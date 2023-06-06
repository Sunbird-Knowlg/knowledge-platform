resource "azurerm_virtual_network" "vnet" {
  name                = "${local.environment_name}"
  location            = var.location
  resource_group_name = data.azurerm_resource_group.rg.name
  address_space       = var.vnet_cidr
  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}