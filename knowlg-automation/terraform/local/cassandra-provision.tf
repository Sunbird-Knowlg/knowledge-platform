resource "helm_release" "cassandra" {
  name             = "cassandra"
  chart            = var.CASSANDRA_CHART
  namespace        = var.CASSANDRA_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  
}
 