resource "helm_release" "taxonomy" {
  name             = "taxonomy"
  chart            = var.TAXONOMY_CHART
  namespace        = var.TAXONOMY_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [helm_release.search]
  wait_for_jobs    = true
  
}
 