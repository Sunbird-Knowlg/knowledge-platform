# resource "helm_release" "search" {
#   name             = "search"
#   chart            = var.SEARCH_CHART
#   namespace        = var.SEARCH_NAMESPACE
#   create_namespace = true
#   dependency_update = true
#   depends_on       = [kind_cluster.one-click]
#   wait_for_jobs    = true
  
# }