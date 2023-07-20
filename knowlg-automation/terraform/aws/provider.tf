provider "aws" {
  region = "us-east-1"
}
/*resource "aws_resourcegroups_group" "resource_group" {
  name = "knowlg-testing"
}*/
data "aws_eks_cluster_auth" "cluster_auth" {
  name = aws_eks_cluster.name
}
data "aws_availability_zones" "zones_available" {}
//data "aws_resourcegroupstaggingapi_resources" "resource_tag" {}

provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.eks.kube_config.0.host
    cluster_ca_certificate = base64decode(aws_eks_cluster.eks.kube_config.0.cluster_ca_certificate)
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.eks.name]
      command     = "aws"
    }
  }
}
