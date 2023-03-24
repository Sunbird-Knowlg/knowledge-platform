terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "=3.14.0"
    }
    # postgresql = {
    #   source = "cyrilgdn/postgresql"
    #   version = "=1.16.0"
    # }
  }
}

# Configure the Microsoft Azure Provider
provider "azurerm" {
  features {}
  skip_provider_registration=true
}

data "azurerm_resource_group" "rg" {
  name = var.RESOURCE_GROUP
}

resource "azurerm_virtual_network" "observ-vnet" {
  name                = "observ-virtual-network"
  resource_group_name = data.azurerm_resource_group.rg.name
  location            = "${var.location}"
  address_space       = ["10.0.0.0/16"]
}



resource "azurerm_subnet" "observ-subnet" {
  name                 = "observ-virtual-subnet"
  resource_group_name  = data.azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.observ-vnet.name
  address_prefixes     = ["10.0.0.0/16"]
  service_endpoints    = ["Microsoft.Sql"]
}
