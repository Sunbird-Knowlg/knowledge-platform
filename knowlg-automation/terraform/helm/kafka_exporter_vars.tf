variable "kafka_exporter_release_name" {
    type        = string
    description = "Kafka exporter helm release name."
    default     = "kafka-exporter"
}

variable "kafka_exporter_namespace" {
    type        = string
    description = "Kafka exporter namespace."
    default     = "kafka"
}

variable "kafka_exporter_chart_path" {
    type        = string
    description = "Kafka exporter chart path."
    default     = "../../helm_charts/kafka-exporter"
}

variable "kafka_exporter_create_namespace" {
    type        = bool
    description = "Create kakfa exporter namespace."
    default     = true
}

variable "kafka_exporter_wait_for_jobs" {
    type        = bool
    description = "Kafka exporter wait for jobs paramater."
    default     = true
}

variable "kafka_exporter_chart_template" {
    type = string
    default = "../../helm_charts/kafka-exporter/values.yaml"
    
}