resource "aws_eks_cluster" "eks_master" {
    name = "${var.building_block}-${var.env}-eks"
    role_arn = aws_iam_role.eks_master_role.arn
    version = var.eks_version
   
    vpc_config {
     subnet_ids = aws_subnet.public_subnets[*].id
    }

    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-eks"
      },
      local.common_tags,
      var.additional_tags)

  depends_on = [
    aws_iam_role_policy_attachment.AmazonEKSClusterPolicy
  ]
}