resource "aws_eks_cluster" "eks_cluster" {
  name  = var.cluster_name
  role_arn = aws_iam_role.eks.arn
   vpc_config {
    subnet_ids = [
      aws_subnet.private-us-west-1a.id,
      aws_subnet.private-us-west-1b.id,
      aws_subnet.public-us-west-1a.id,
      aws_subnet.public-us-west-1b.id
    ]
  }
  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster,
  ]

  tags = merge(
      local.common_tags,
      var.additional_tags
      )
}

resource "aws_iam_role" "eks" {
  name = "eks_cluster_role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "eks.amazonaws.com"
        }
      },
    ]
  })
  tags = merge(
    local.common_tags,
    var.additional_tags
  )

}
resource "aws_iam_role_policy_attachment" "eks_cluster" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks.name
}
  