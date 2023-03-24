resource "aws_eks_node_group" "eks_nodes" {
    cluster_name  = aws_eks_cluster.eks_master.name
    node_group_name = var.node_group_name
    node_role_arn  = aws_iam_role.eks_nodes_role.arn
    subnet_ids   = aws_subnet.public_subnets[*].id
    ami_type = var.eks_node_group_ami_type
    instance_types = var.eks_node_group_instance_type
    capacity_type = var.eks_node_group_capacity_type

    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-nodegroup-${var.eks_node_group_capacity_type}"
      },
      local.common_tags,
      var.additional_tags)
   
    scaling_config {
        desired_size = var.eks_node_group_scaling_config["desired_size"]
        max_size = var.eks_node_group_scaling_config["max_size"]
        min_size = var.eks_node_group_scaling_config["min_size"]
    }

  depends_on = [
    aws_iam_role_policy_attachment.AmazonEKSWorkerNodePolicy,
    aws_iam_role_policy_attachment.AmazonEKS_CNI_Policy,
    aws_iam_role_policy_attachment.AmazonEC2ContainerRegistryReadOnly,
    aws_iam_role_policy_attachment.AmazonEBSCSIDriverPolicy
  ]
}