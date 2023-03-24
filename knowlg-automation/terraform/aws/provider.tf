provider "aws" {
    region  = var.region
}

provider "helm" {
    kubernetes {
        host                   = aws_eks_cluster.eks_master.endpoint
        cluster_ca_certificate = base64decode(aws_eks_cluster.eks_master.certificate_authority.0.data)
        exec {
            api_version = "client.authentication.k8s.io/v1beta1"
            command    = "aws"
            args       = ["--region", "${var.region}", "eks", "get-token", "--cluster-name", "${var.building_block}-${var.env}-eks"] 
        }
    }
}