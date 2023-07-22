provider "aws" {
  region = var.region
}
data "aws_availability_zones" "zones_available" {}
data "aws_eks_cluster" "eks_cluster" {
  name = var.cluster_name
}

resource "aws_iam_role" "eks" {
  name = "eks_cluster"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })
  tags = merge(
      local.common_tags,
      var.additional_tags
      )

  }
  resource "aws_iam_role_policy_attachment" "eks_cluster" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks.name
}
provider "helm" {
  kubernetes {
   // host                   = aws_eks_cluster.eks_cluster.kube_config.0.host
   // cluster_ca_certificate = base64decode(aws_eks_cluster.eks_cluster.kube_config.0.cluster_ca_certificate)
   /* exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.eks.name]
      command     = "aws"
    }*/
  }
}
