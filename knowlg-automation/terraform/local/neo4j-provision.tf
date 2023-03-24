resource "helm_release" "neo4j" {
  name             = "neo4j"
  chart            = var.NEO4J_CHART
  namespace        = var.NEO4J_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  
}