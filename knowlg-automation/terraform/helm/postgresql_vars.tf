variable "postgresql_release_name" {
    type        = string
    description = "Postgresql helm release name."
    default     = "postgresql"
}

variable "postgresql_namespace" {
    type        = string
    description = "Postgresql namespace."
    default     = "postgresql"
}

variable "postgresql_install_timeout" {
    type        = number
    description = "Postgresql chart install timeout."
    default     = 600
}

variable "postgresql_create_namespace" {
    type        = bool
    description = "Create postgresql namespace."
    default     = true
}

variable "postgresql_template" {
    type        = string
    description = "Postgresql chart custom values.yaml path."
    default     = "../terraform_helm_templates/postgres.yaml.tfpl"
}

variable "postgresql_dependecy_update" {
    type        = bool
    description = "Postgresql chart dependency update."
    default     = true
}

variable "postgresql_repository" {
    type        = string
    description = "Postgresql chart repository url."
    default     = "https://charts.bitnami.com/bitnami"
}

variable "postgresql_name" {
    type        = string
    description = "Postgresql chart name."
    default     = "postgresql"
}

variable "postgresql_version" {
    type        = string
    description = "postgresql chart version."
    default     = "11.9.1"
}