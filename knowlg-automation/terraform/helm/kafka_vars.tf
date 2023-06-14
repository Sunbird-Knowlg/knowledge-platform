variable "kafka_release_name" {
    type        = string
    description = "Kafka helm release name."
    default     = "kafka"
}

variable "kafka_namespace" {
    type        = string
    description = "Kafka namespace."
    default     = "knowlg-db"
}

variable "kafka_chart_path" {
    type        = string
    description = "Kafka chart path."
    default     = "../../helm_charts/kafka"
}

variable "kafka_chart_install_timeout" {
    type        = number
    description = "Kafka chart install timeout."
    default     = 3000
}

variable "kafka_create_namespace" {
    type        = bool
    description = "Create kafka namespace."
    default     = true
}

variable "kafka_wait_for_jobs" {
    type        = bool
    description = "Kafka wait for jobs paramater."
    default     = true
}

variable "kafka_chart_custom_values_yaml" {
    type        = string
    description = "Kafka chart values.yaml path."
    default     = "../../helm_charts/kafka/values.yaml"
}

variable "kafka_chart_dependecy_update" {
    type        = bool
    description = "Kafka chart dependency update."
    default     = true
}

variable "kafka_content_publish_topic" {
    type        = string
    description = "Kafka content publish topic."
    default     = "publish.job.request"
}

variable "kafka_content_postpublish_topic" {
    type        = string
    description = "Kafka content postpublish topic"
    default     = "content.postpublish.request"
}

variable "kafka_learning_job_request_topic" {
    type        = string
    description = "Kafka learning_job_request topic"
    default     = "learning_job_request"
}

variable "kafka_learning_graph_events_topic" {
    type        = string
    description = "Kafka learning.graph.events topic"
    default     = "learning.graph.events"
}

variable "kafka_learning_events_failed_topic" {
    type        = string
    description = "Kafka learning.events.failed topic"
    default     = "learning.events.failed"
}

variable "kafka_search_indexer_group_topic" {
    type        = string
    description = "Kafka search-indexer-group topic"
    default     = "search-indexer-group"
}

variable "kafka_qrimage_request_topic" {
    type        = string
    description = "Kafka qrimage.request topic"
    default     = "qrimage.request"
}

variable "kafka_telemetry_raw_topic" {
    type        = string
    description = "Kafka telemetry.raw topic"
    default     = "telemetry.raw"
}

variable "kafka_dialcode_context_job_request_topic" {
    type        = string
    description = "Kafka dialcode.context.job.request topic"
    default     = "dialcode.context.job.request"
}

variable "kafka_dialcode_context_job_request_failed_topic" {
    type        = string
    description = "Kafka dialcode.context.job.request topic"
    default     = "dialcode.context.job.request.failed"
}