variable "superset_release_name" {
    type        = string
    description = "Superset helm release name."
    default     = "superset"
}

variable "superset_namespace" {
    type        = string
    description = "Superset namespace."
    default     = "superset"
}

variable "superset_chart_path" {
    type        = string
    description = "Superset chart path."
    default     = "../../helm_charts/superset-helm"
}

variable "superset_chart_install_timeout" {
    type        = number
    description = "Superset chart install timeout."
    default     = 3000
}

variable "superset_create_namespace" {
    type        = bool
    description = "Create superset namespace."
    default     = true
}

variable "superset_wait_for_jobs" {
    type        = bool
    description = "Superset wait for jobs paramater."
    default     = true
}

variable "superset_chart_custom_values_yaml" {
    type        = string
    description = "Superset chart values.yaml path."
    default     = "../../helm_charts/superset-helm/values.yaml"
}