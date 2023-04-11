variable "env" {
    type        = string
    description = "Environment name. All resources will be prefixed with this value."
    default     = "dev"
}

variable "building_block" {
    type        = string
    description = "Building block name. All resources will be prefixed with this value."
    default     = "knowlg"
}

variable "location" {
    type        = string
    description = "Azure location to create the resources."
    default     = "East US 2"
}

variable "additional_tags" {
    type        = map(string)  
    description = "Additional tags for the resources. These tags will be applied to all the resources."
    default     = {}
}

variable "vnet_cidr" {
    type        = list(string)
    description = "Azure vnet CIDR range."
    default     = ["10.0.0.0/16"]
}

variable "aks_subnet_cidr" {
    type        = list(string)
    description = "Azure AKS subnet CIDR range."
    default     = ["10.0.0.0/22"]
}

variable "aks_subnet_service_endpoints" {
    type        = list(string)
    description = "Azure AKS subnet service endpoints."
    default     = ["Microsoft.Sql", "Microsoft.Storage"]
}

variable "azure_storage_tier" {
    type        = string
    description = "Azure storage tier - Standard / Premium."
    default     = "Standard"
}

variable "azure_storage_replication" {
    type        = string
    description = "Azure storage replication - LRS / ZRS / GRS etc."
    default     = "LRS"
}

variable "aks_nodepool_name" {
    type        = string
    description = "AKS node pool name."
    default     = "aksnodepool1"
}

variable "aks_node_count" {
    type        = number
    description = "AKS node count."
    default     = 4
}

variable "aks_node_size" {
    type        = string
    description = "AKS node size."
    default     = "Standard_D2s_v4"
}

variable "aks_cluster_identity" {
    type        = string
    description = "AKS cluster identity."
    default     = "SystemAssigned"
}

variable "kubernetes_storage_class" {
    type        = string
    description = "Storage class name for the AKS cluster"
    default     = "default"
}

variable "druid_deepstorage_type" {
    type        = string
    description = "Druid deep strorage type."
    default     = "azure"
}

variable "flink_checkpoint_store_type" {
    type        = string
    description = "Flink checkpoint store type."
    default     = "azure"
}

variable "RESOURCE_GROUP" {
    type        = string
    description = "RESOURCE GROUP name"
    default     = "knowlg-testing"
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

#CASSANDRA
variable "CASSANDRA_CHART" {
  description = "Cassandra Instance Running Namespace"
  default = "../../helm_charts/cassandra"
}

variable "CASSANDRA_NAMESPACE" {
  description = "CASSANDRA Instance Running Namespace"
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

#REDIS
variable "REDIS_CHART" {
  description = "Redis Instance Running Namespace"
  default = "../../helm_charts/redis"
}

variable "REDIS_NAMESPACE" {
  description = "Redis Instance Running Namespace"
  default     = "knowlg-db"
}

#CONTENT
variable "CONTENT_CHART" {
  description = "Content Instance Running Namespace"
  default     = "../../helm_charts/content"
}

variable "CONTENT_NAMESPACE" {
  description = "Content Instance Running Namespace"
  default     = "knowlg-api"
}

#SEARCH
variable "SEARCH_CHART" {
  description = "Search Instance Running Namespace"
  default     = "../../helm_charts/search"
}

variable "SEARCH_NAMESPACE" {
  description = "Search Instance Running Namespace"
  default     = "knowlg-api"
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

#LEARNING
variable "LEARNING_CHART" {
  description = "Learning Instance Running Namespace"
  default     = "../../helm_charts/learning"
}

variable "LEARNING_NAMESPACE" {
  description = "Learning Instance Running Namespace"
  default     = "knowlg-api"
}