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
  default     = "us-west-2" # Replace with your desired AWS region
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
variable "subnet_cidr" {
  type        = string
  description = "AWS subnet CIDR range."
  default     = "10.0.0.0/22"
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
variable "storage_s3" {
  type    = string
  default = "S3"

}
variable "inst_type" {
    type        = list(string)
    description = "instance types"
    default     = ["t2.mciro", "t3.micro"]
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