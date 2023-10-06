resource "aws_eks_node_group" "node_grp" {
  cluster_name    = aws_eks_cluster.eks_cluster.name
  node_group_name = "node_grp"
  node_role_arn   = aws_iam_role.eks_node_group.arn
  
  subnet_ids = [
    aws_subnet.private-us-west-1a.id,
    aws_subnet.private-us-west-1b.id
  ]

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

resource "aws_iam_role" "eks_node_group" {
  name = "eks-node-group-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_node_group_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node_group.name
}
resource "aws_iam_role_policy_attachment" "eks_cni_attachment" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_node_group.name
}

resource "aws_iam_role_policy_attachment" "AmazonEC2ContainerRegistryReadOnly" {
    policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
    role    = aws_iam_role.eks_node_group.name
}

resource "aws_iam_role_policy_attachment" "AmazonEBSCSIDriverPolicy" {
    policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
    role    = aws_iam_role.eks_node_group.name
}
