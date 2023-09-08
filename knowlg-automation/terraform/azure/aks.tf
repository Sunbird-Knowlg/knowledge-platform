resource "azurerm_kubernetes_cluster" "aks" {
  name                = "${local.environment_name}"
  location            = var.location
  resource_group_name = data.azurerm_resource_group.rg.name
  dns_prefix          = "${local.environment_name}"

  default_node_pool {
    name       = var.aks_nodepool_name
    node_count = var.aks_node_count
    vm_size    = var.aks_node_size
  }

  identity {
    type = var.aks_cluster_identity
  }

  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}