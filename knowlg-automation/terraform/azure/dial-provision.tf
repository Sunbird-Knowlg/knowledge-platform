resource "helm_release" "dial" {
  name             = "dial"
  chart            = var.DIAL_CHART
  namespace        = var.DIAL_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [helm_release.learning]
  wait_for_jobs    = true

}