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
variable "additional_tags" {
  type        = map(string)
  description = "Additional tags for the resources. These tags will be applied to all the resources."
  default     = {}
}
variable "region" {
  description = "AWS region for resources"
  default     = "us-west-1" # Replace with your desired AWS region
}
variable "cluster_name" {
  description = "Name of the EKS cluster"
  default     = "my-eks-cluster" # Replace with your desired cluster name
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
  default     = "../../helm_charts/cassandra"
}

variable "CASSANDRA_NAMESPACE" {
  description = "CASSANDRA Instance Running Namespace"
  default     = "knowlg-db"
}

#NEO4J
variable "NEO4J_CHART" {
  description = "Neo4j Instance Running Namespace"
  default     = "../../helm_charts/neo4j"
}

variable "NEO4J_NAMESPACE" {
  description = "NEO4J Instance Running Namespace"
  default     = "knowlg-db"
}

#REDIS
variable "REDIS_CHART" {
  description = "Redis Instance Running Namespace"
  default     = "../../helm_charts/redis"
}

variable "REDIS_NAMESPACE" {
  description = "Redis Instance Running Namespace"
  default     = "knowlg-db"
}
variable "vpc_cidr" {
  type        = string
  description = "AWS vpc CIDR range."
  default     = "10.0.0.0/16"
}

variable "destination_cidr" {
  type        = string
  description = "destination cider for route"
  default     = "0.0.0.0/0"
}
variable "max_count" {
  type        = number
  description = "EKS node count."
  default     = 2
}
variable "min_count" {
  type        = number
  description = "EKS node count."
  default     = 1
}
variable "desired_count" {
  type        = number
  description = "AKS node count."
  default     = 1
}

variable "inst_type" {
    type        = list(string)
    description = "instance types"
    default     = ["t3.micro"]
}
variable "eks_addons" {
  type = list(object({
    name    = string
    version = string
  }))

  default = [
    {
      name    = "kube-proxy"
      version = "v1.24.9-eksbuild.1"
    },
    {
      name    = "vpc-cni"
      version = "v1.12.1-eksbuild.1"
    },
    {
      name    = "coredns"
      version = "v1.8.7-eksbuild.3"
    },
    {
      name    = "aws-ebs-csi-driver"
      version = "v1.14.1-eksbuild.1"
    }
  ]
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

#DIAL
variable "DIAL_CHART" {
  description = "DIAL Instance Running Namespace"
  default     = "../../helm_charts/dial"
}

variable "DIAL_NAMESPACE" {
  description = "DIAL Instance Running Namespace"
  default     = "knowlg-api"
}
#FLINK
variable "flink_release_name" {
  type        = list(string)
  description = "Flink helm release name."
  default     = ["search-indexer","audit-event-generator","asset-enrichment","post-publish-processor","dialcode-context-updater", "qrcode-image-generator","video-stream-generator","audit-history-indexer"]
  # default     = ["merged-pipeline"]
}

variable "flink_namespace" {
    type        = string
    description = "Flink namespace."
    default     = "knowlg-job"
}

variable "flink_chart_path" {
    type        = string
    description = "Flink chart path."
    default     = "../../helm_charts/flink"
}

variable "flink_chart_install_timeout" {
    type        = number
    description = "Flink chart install timeout."
    default     = 900
}

variable "flink_create_namespace" {
    type        = bool
    description = "Create flink namespace."
    default     = true
}

variable "flink_wait_for_jobs" {
    type        = bool
    description = "Flink wait for jobs paramater."
    default     = false
}

variable "flink_chart_template" {
    type        = string
    description = "Flink chart values.yaml path."
    default     = "../terraform_helm_templates/flink.yaml.tfpl"
}

variable "flink_kubernetes_service_name" {
    type        = string
    description = "Flink kubernetes service name."
    default     = "asset-enrichment-jobmanager"
}
