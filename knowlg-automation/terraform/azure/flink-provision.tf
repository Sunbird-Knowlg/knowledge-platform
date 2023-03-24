resource "helm_release" "flink" {
  name             = "druid-validator"
  chart            = var.FLINK_CHART
  namespace        = var.FLINK_NAMESPACE
  create_namespace = true
  depends_on       = [helm_release.kafka]
  wait_for_jobs    = false
  timeout = 900
  
  values = [
    templatefile("../../helm_charts/flink/values.yaml",
    {
      //storage_class_name = var.PERSISTENT_STORAGE_CLASS
    })
  ]
}
data "kubernetes_service" "flink" {
  metadata {
    namespace = "flink-dev"
    name = "druid-validator-jobmanager"
  }
depends_on       = [azurerm_kubernetes_cluster.aks,helm_release.kafka]
}