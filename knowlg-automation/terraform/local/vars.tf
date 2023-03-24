variable "kind_cluster_name" {
  type        = string
  description = "The name of the cluster."
  default     = "one-click"
}

variable "kind_cluster_config_path" {
  type        = string
  description = "The location where this cluster's kubeconfig will be saved to."
  default = "~/.kube/config"
}

variable "kube_config_context" {
  type        = string
  description = "The config context in kubeconfig"
  default = "kind-one-click"
 }

variable "ingress_nginx_helm_version" {
  type        = string
  description = "The Helm version for the nginx ingress controller."
  default     = "4.0.6"
}

variable "ingress_nginx_namespace" {
  type        = string
  description = "The nginx ingress namespace (it will be created if needed)."
  default     = "ingress-nginx"
}


variable "STAGE" {
  description = "Deployment Stage"
  default     = "dev"
}

#DRUID
variable "DRUID_CLUSTER_CHART" {
  description = "Druid Instance Running Namespace"
  default     = "../../helm_charts/druid-cluster"
}

variable "DRUID_NAMESPACE" {
  description = "Druid Instance Running Namespace"
  default     = "druid-raw"
}

variable "DRUID_OPERATOR_CHART" {
  description = "Druid Instance Running Namespace"
  default     = "../../helm_charts/druid-operator"
}

variable "DRUID_MIDDLE_MANAGER_WORKER_CAPACITY" {
  type    = number
  default = 2
}

variable "DRUID_MIDDLE_MANAGER_PEON_HEAP" {
  type    = string
  default = "256M"
}

#KAFKA
variable "KAFKA_CHART" {
  description = "Kafka Instance Running Namespace"
  default     = "../../helm_charts/kafka"
}

variable "KAFKA_NAMESPACE" {
  description = "Kafka Instance Running Namespace"
  default     = "knowlg-db"
}

#ELASTICSEARCH
variable "ELASTICSEARCH_CHART" {
  description = "Elasticsearch Instance Running Namespace"
  default     = "../../helm_charts/elasticsearch"
}

variable "ELASTICSEARCH_NAMESPACE" {
  description = "Elasticsearch Instance Running Namespace"
  default     = "knowlg-db"
}


#CASSANDRA

variable "CASSANDRA_CHART" {
  description = "Cassandra Instance Running Namespace"
  default = "../../helm_charts/cassandra"
}

variable "CASSANDRA_NAMESPACE" {
  description = "CASSANDRA Instance Running Namespace"
  default     = "knowlg-db"
}

#NEO4J

variable "NEO4J_CHART" {
  description = "Neo4j Instance Running Namespace"
  default = "../../helm_charts/neo4j"
}

variable "NEO4J_NAMESPACE" {
  description = "NEO4J Instance Running Namespace"
  default     = "knowlg-db"
}

#REDIS

variable "REDIS_CHART" {
  description = "Redis Instance Running Namespace"
  default = "../../helm_charts/redis"
}

variable "REDIS_NAMESPACE" {
  description = "Redis Instance Running Namespace"
  default     = "knowlg-db"
}

#LOGSTASH

variable "LOGSTASH_CHART" {
  description = "Redis Instance Running Namespace"
  default = "../../helm_charts/logstash"
}

variable "LOGSTASH_NAMESPACE" {
  description = "Logstash Instance Running Namespace"
  default     = "knowlg-db"
}

#TAXONOMY
variable "TAXONOMY_CHART" {
  description = "Taxonomy Instance Running Namespace"
  default     = "../../helm_charts/taxonomy"
}

variable "TAXONOMY_NAMESPACE" {
  description = "Taxonomy Instance Running Namespace"
  default     = "knowlg-api"
}


#SUPERSET

variable "SUPERSET_NAMESPACE" {
  type    = string
  default = "superset"
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

