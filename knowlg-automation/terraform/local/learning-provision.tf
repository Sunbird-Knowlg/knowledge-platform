resource "helm_release" "learning" {
  name             = "learning"
  chart            = var.LEARNING_CHART
  namespace        = var.LEARNING_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  
}