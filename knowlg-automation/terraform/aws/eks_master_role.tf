resource "aws_iam_role" "eks_master_role" {
    name = var.eks_master_role
    assume_role_policy = <<POLICY
    {
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": {
            "Service": "eks.amazonaws.com"
          },
          "Action": "sts:AssumeRole"
        }
      ]
    }
    POLICY
    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-eks-master-policy"
      },
      local.common_tags,
      var.additional_tags)
}