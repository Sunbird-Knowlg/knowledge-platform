
resource "helm_release" "elasticsearch" {
  name             = "elasticsearch"
  chart            = var.ELASTICSEARCH_CHART
  namespace        = var.ELASTICSEARCH_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  
}
