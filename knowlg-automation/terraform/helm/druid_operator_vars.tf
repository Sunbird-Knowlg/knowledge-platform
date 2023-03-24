variable "druid_operator_release_name" {
    type        = string
    description = "Druid operator helm release name."
    default     = "druid-operator"
}

variable "druid_operator_namespace" {
    type        = string
    description = "Druid operator namespace."
    default     = "druid-raw"
}

variable "druid_operator_chart_path" {
    type        = string
    description = "Druid operator chart path."
    default     = "../../helm_charts/druid-operator"
}

variable "druid_operator_chart_install_timeout" {
    type        = number
    description = "Druid operator chart install timeout."
    default     = 600
}

variable "druid_operator_create_namespace" {
    type        = bool
    description = "Create druid operator namespace."
    default     = true
}

variable "druid_operator_wait_for_jobs" {
    type        = bool
    description = "Druid operator wait for jobs paramater."
    default     = true
}