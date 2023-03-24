resource "helm_release" "superset" {
    name             = var.superset_release_name
    chart            = var.superset_chart_path
    namespace        = var.superset_namespace
    create_namespace = var.superset_create_namespace
    wait_for_jobs    = var.superset_wait_for_jobs
    values           = [
      templatefile(var.superset_chart_custom_values_yaml,{})
    ]
}