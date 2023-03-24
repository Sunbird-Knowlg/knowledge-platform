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
          input_topic = "${var.env}.${var.kafka_input_topic}"
          output_telemetry_route_topic = "${var.env}.${var.kafka_output_telemetry_route_topic}"
          output_summary_route_topic = "${var.env}.${var.kafka_output_summary_route_topic}"
          output_failed_topic = "${var.env}.${var.kafka_output_failed_topic}"
          output_duplicate_topic = "${var.env}.${var.kafka_output_duplicate_topic}"
        }
      )
    ]
}