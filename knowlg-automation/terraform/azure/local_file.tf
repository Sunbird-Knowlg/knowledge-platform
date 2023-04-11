resource "local_file" "kubeconfig" {
    content      = azurerm_kubernetes_cluster.aks.kube_config_raw
    filename     = "${local.environment_name}-kubeconfig.yaml"
}
