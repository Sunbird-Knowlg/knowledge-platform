resource "local_file" "kubeconfig" {
    content      = data.aws_eks_cluster.eks_cluster
    filename     = "${local.environment_name}-kubeconfig.yaml"
}