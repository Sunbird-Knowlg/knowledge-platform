resource "helm_release" "kafka" {
    name              = var.kafka_release_name
    chart             = var.kafka_chart_path
    namespace         = var.kafka_namespace
    create_namespace  = var.kafka_create_namespace
    dependency_update = var.kafka_chart_dependecy_update
    wait_for_jobs     = var.kafka_wait_for_jobs
    values = [
      templatefile(var.kafka_chart_custom_values_yaml,
        {
          content_publish_topic = "${var.env}.${var.kafka_content_publish_topic}"
          content_postpublish_topic = "${var.env}.${var.kafka_content_postpublish_topic}"
          learning_job_request_topic = "${var.env}.${var.kafka_learning_job_request_topic}"
          learning_graph_events_topic = "${var.env}.${var.kafka_learning_graph_events_topic}"
          learning_events_failed_topic = "${var.env}.${var.kafka_learning_events_failed_topic}"
          search_indexer_group_topic = "${var.env}.${var.kafka_search_indexer_group_topic}"
          qrimage_request_topic = "${var.env}.${var.kafka_qrimage_request_topic}"
          telemetry_raw_topic = "${var.env}.${var.kafka_telemetry_raw_topic}"
          dialcode_context_job_request_topic = "${var.env}.${var.kafka_dialcode_context_job_request_topic}"
          dialcode_context_job_request_failed_topic = "${var.env}.${var.kafka_dialcode_context_job_request_failed_topic}"
        }
      )
    ]
}












