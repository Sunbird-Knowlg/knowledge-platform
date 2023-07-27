resource "aws_eks_cluster" "eks_cluster" {
  name  = var.cluster_name
  role_arn = aws_iam_role.eks.arn
  version = "1.25"

  vpc_config {
    subnet_ids = [aws_subnet.eks_subnet.id]
  }
  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster,
  ]

  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}
  