
resource "helm_release" "logstash" {
  name             = "logstash"
  chart            = var.LOGSTASH_CHART
  namespace        = var.LOGSTASH_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  
}
