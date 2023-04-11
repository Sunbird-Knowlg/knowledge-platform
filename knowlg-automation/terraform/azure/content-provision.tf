resource "helm_release" "content" {
  name             = "content"
  chart            = var.CONTENT_CHART
  namespace        = var.CONTENT_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [helm_release.redis]
  wait_for_jobs    = true
  
}
 