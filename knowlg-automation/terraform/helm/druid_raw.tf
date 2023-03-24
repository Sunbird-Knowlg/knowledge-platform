resource "helm_release" "druid_cluster" {
    name             = var.druid_cluster_release_name
    chart            = var.druid_cluster_chart_path
    timeout          = var.druid_cluster_chart_install_timeout
    namespace        = var.druid_cluster_namespace
    create_namespace = var.druid_cluster_create_namespace
    depends_on       = [helm_release.druid_operator, helm_release.postgres]
    wait_for_jobs    = var.druid_cluster_wait_for_jobs
    values = [
      templatefile(var.druid_cluster_chart_template,
        {
          druid_namespace          = var.druid_cluster_namespace
          druid_user               = var.druid_user
          druid_password           = var.druid_password
          druid_worker_capacity    = var.druid_worker_capacity
          env                      = var.env
          kubernetes_storage_class = var.kubernetes_storage_class
          druid_deepstorage_type   = var.druid_deepstorage_type
          s3_bucket                = coalesce(local.s3_bucket, "")
          s3_access_key            = coalesce(local.s3_access_key, "")
          s3_secret_key            = coalesce(local.s3_secret_key, "")
        }
      )
    ]
}