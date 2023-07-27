resource "aws_eks_node_group" "node_grp" {
  cluster_name    = aws_eks_cluster.eks_cluster.name
  node_group_name = "node_grp"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  subnet_ids      = aws_subnet.eks_subnet.id
  instance_types = var.inst_type
  capacity_type = "SPOT"
  
  scaling_config {
    desired_size = var.desired_count
    max_size     = var.max_count
    min_size     = var.min_count
  }
  depends_on = [
    aws_iam_role_policy_attachment.eks_node_group_attachment,
    aws_iam_role_policy_attachment.eks_cni_attachment,
    aws_iam_role_policy_attachment.AmazonEC2ContainerRegistryReadOnly,
    aws_iam_role_policy_attachment.AmazonEBSCSIDriverPolicy
  ]
}