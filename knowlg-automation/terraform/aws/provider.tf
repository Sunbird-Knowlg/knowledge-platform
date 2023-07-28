terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
provider "aws" {
  region = var.region
}

data "aws_availability_zones" "zones_available" {}
data "aws_eks_cluster" "eks_cluster" {
  name = aws_eks_cluster.eks_cluster.name
}
output "endpoint" {
  value = data.aws_eks_cluster.eks_cluster.endpoint
}
provider "helm" {
  kubernetes {
     host                   = data.aws_eks_cluster.eks_cluster.endpoint
     cluster_ca_certificate = base64decode(data.aws_eks_cluster.eks_cluster.certificate_authority.0.data)
     exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      args        = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.eks_cluster.name]
      command     = "aws"
    }
  }
}
