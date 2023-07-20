resource "local_file" "kubeconfig" {
    content      = aws_eks_cluster.eks.kube_config_raw
    filename     = "${local.environment_name}-kubeconfig.yaml"
}
