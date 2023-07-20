resource "aws_eks_cluster" "eks" {
  name  = "${local.environment_name}"
  //dns_prefix  = "${local.environment_name}"
  role_arn = aws_iam_role.example.arn


  vpc_config {
    subnet_ids = [aws_subnet.eks_subnet.id]
  }

  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}
  