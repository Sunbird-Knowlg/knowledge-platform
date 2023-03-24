variable "location" {
default = "centralindia"
}
variable "RESOURCE_GROUP" {
default = "Obsrv-testing"
}

variable "KUBE_CONFIG_FILENAME" {
  description = "Druid Instance Running Namespace"
  default     = "../aks.yaml"
}

variable "DRUID_NAMESPACE" {
  description = "Druid Instance Running Namespace"
  default     = "druid-raw"
}

variable "FLINK_NAMESPACE" {
  description = "Druid Instance Running Namespace"
  default     = "flink-dev"
}

variable "DRUID_CLUSTER_CHART" {
  description = "Druid Instance Running Namespace"
  default     = "../../helm_charts/druid-cluster"
}

variable "FLINK_CHART" {
  description = "Flink chart"
  default     = "../../helm_charts/flink"
}

variable "DRUID_OPERATOR_CHART" {
  description = "Druid Instance Running Namespace"
  default     = "../../helm_charts/druid-operator"
}

variable "NAME_INGESTION_SPEC"{
  default     = "2020-06-04-1591233567703.json.gz"
}

variable "PATH_INGESTION_SPEC"{
  default     = "../sample-data/2020-06-04-1591233567703.json.gz"
}

variable "KAFKA_CHART" {
  description = "Kafka Instance Running Namespace"
  default     = "../../helm_charts/kafka"
}

variable "STAGE" {
  description = "Deployment Stage"
  default     = "dev"
}

variable "STORAGE_ACCOUNT" {
  description = "Deployment Stage"
  default     = "obsrvacc"
}

variable "PERSISTENT_STORAGE_CLASS" {
  description = "Persistent Storage Class Name"
  default     = "default"
}


variable "KUBE_CONFIG_PATH" {
  description = "Path of the kubeconfig file"
  type        = string
  default     = "../aks.yaml"
}

variable "DRUID_RDS_DB_NAME" {
  description = "DB name of Druid postgress"
  type        = string
  default     = "druid_raw"
}

variable "RDS_INSTANCE_TYPE" {
  description = "Postgres Instance Type"
  type        = string
  default     = "db.t3.micro"
}
// Currently used only in the AWS_DB_INSTANCE Resource
variable "APPLY_IMMEDIATELY" {
  type = bool
  description = "Apply changes immediately"
  default = true
}

variable "RDS_PASSWORD" {
  description = "Password of RDS DB"
  type        = string
  default     = "SanK@2022"
}

variable "RDS_USER" {
  description = "Password of RDS DB"
  type        = string
  default = "postgres"
}

variable "DRUID_RDS_USER" {
  description = "User of Druid postgress"
  type        = string
  default     = "druid"
}

variable "RDS_DRUID_USER_PASSWORD" {
  description = "User of Druid postgress"
  type        = string
  default     = "SanK@2022"
}

variable "DRUID_DEEP_STORAGE_CONTAINER" {
  type    = string
  default = "obsrv-blob"
}

variable "RDS_STORAGE" {
  type    = number
  default = 5120
}

variable "DRUID_MIDDLE_MANAGER_WORKER_CAPACITY" {
  type    = number
  default = 2
}
variable "DRUID_MIDDLE_MANAGER_PEON_HEAP" {
  type    = string
  default = "256M"
}
variable "SUPERSET_NAMESPACE" {
  type    = string
  default = "superset"
}

variable "SUPERSET_RDS_DB_NAME" {
  type    = string
  default = "superset"
}
variable "SUPERSET_RDS_DB_USER" {
  type    = string
  default = "superset"
}

variable "SUPERSET_RDS_DB_PASSWORD" {
  type    = string
  default = "SanK@2022"
}

variable "SUPERSET_ADMIN_USERNAME" {
  type    = string
  default = "admin"
}
variable "SUPERSET_ADMIN_FIRSTNAME" {
  type    = string
  default = "Superset"
}

variable "SUPERSET_ADMIN_LASTNAME" {
  type    = string
  default = "Admin"
}

variable "SUPERSET_ADMIN_PASSWORD" {
  type    = string
default   = "admin123"
}
variable "SUPERSET_ADMIN_EMAIL" {
  type    = string
  default = "admin@superset.com"
}
variable "SUPERSET_RDS_PORT" {
  type    = string
  default = "5432"
}
variable "KAFKA_NAMESPACE" {
  description = "KAFKA Instance Running Namespace"
  default     = "kafka"
}
