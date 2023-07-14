resource "helm_release" "redis" {
  name             = "redis"
  chart            = var.REDIS_CHART
  namespace        = var.REDIS_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [helm_release.elasticsearch]
  wait_for_jobs    = true
  
}