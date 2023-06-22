resource "helm_release" "kafka_exporter" {
    name             = var.kafka_exporter_release_name
    chart            = var.kafka_exporter_chart_path
    namespace        = var.kafka_exporter_namespace
    create_namespace = var.kafka_exporter_create_namespace
    wait_for_jobs    = var.kafka_exporter_wait_for_jobs
    depends_on       = [helm_release.druid_cluster,helm_release.kafka]
    values = [
        templatefile(var.kafka_exporter_chart_template,
        {
            kafka_exporter_namespace        = var.kafka_exporter_namespace
        }
        )
    ]
}