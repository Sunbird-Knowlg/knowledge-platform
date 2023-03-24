variable "monitoring_release_name" {
    type        = string
    description = "Monitoring helm release name."
    default     = "monitoring"
}

variable "monitoring_namespace" {
    type        = string
    description = "Superset namespace."
    default     = "monitoring"
}

variable "monitoring_chart_install_timeout" {
    type        = number
    description = "Monitoring chart install timeout."
    default     = 3000
}

variable "monitoring_create_namespace" {
    type        = bool
    description = "Create monitoring namespace."
    default     = true
}

variable "monitoring_wait_for_jobs" {
    type        = bool
    description = "Monitoring wait for jobs paramater."
    default     = true
}

variable "monitoring_chart_repository" {
    type        = string
    description = "Monitoring chart repository url."
    default     = "https://prometheus-community.github.io/helm-charts"
}

variable "monitoring_chart_name" {
    type        = string
    description = "Monitoring chart name."
    default     = "kube-prometheus-stack"
}

variable "monitoring_chart_version" {
    type        = string
    description = "Monitoring chart version."
    default     = "44.2.1"
}

variable "monitoring_install_timeout" {
    type        = number
    description = "Monitoring chart install timeout."
    default     = 1200
}