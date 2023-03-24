resource "aws_iam_role" "eks_nodes_role" {
    name = var.eks_nodes_role
    assume_role_policy = jsonencode({
     Version = "2012-10-17"
     Statement = [{
      Effect = "Allow"
      Principal = {
       Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
     }]
    })
    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-eks-nodes-policy"
      },
      local.common_tags,
      var.additional_tags)
}