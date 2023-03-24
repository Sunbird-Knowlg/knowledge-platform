variable "druid_cluster_release_name" {
    type        = string
    description = "Druid cluster helm release name."
    default     = "druid-cluster"
}

variable "druid_cluster_namespace" {
    type        = string
    description = "Druid namespace."
    default     = "druid-raw"
}

variable "druid_cluster_chart_path" {
    type        = string
    description = "Druid cluster chart path."
    default     = "../../helm_charts/druid-cluster"
}

variable "druid_cluster_chart_install_timeout" {
    type        = number
    description = "Druid cluster chart install timeout."
    default     = 3000
}

variable "druid_cluster_create_namespace" {
    type        = bool
    description = "Create druid cluster namespace."
    default     = true
}

variable "druid_cluster_wait_for_jobs" {
    type        = bool
    description = "Druid cluster wait for jobs paramater."
    default     = true
}

variable "druid_cluster_chart_template" {
    type        = string
    description = "Druid cluster chart values.yaml path."
    default     = "../terraform_helm_templates/druid_cluster.yaml.tfpl"
}

variable "druid_user" {
    type        = string
    description = "Druid user name."
    default     = "druid"
}

variable "druid_password" {
    type        = string
    description = "Druid password."
    default     = "druid"
}

variable "druid_worker_capacity" {
    type        = number
    description = "Druid middle manager worker capacity."
    default     = 2
}