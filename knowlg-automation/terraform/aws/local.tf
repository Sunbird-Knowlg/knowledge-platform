
locals {
    common_tags = {
      Environment = "${var.env}"
      BuildingBlock = "${var.building_block}"
    }

    availability_zones = [for zone in ["a", "b", "c"] : "${var.region}${zone}"]

    storage_bucket = "${var.building_block}-${var.env}-${data.aws_caller_identity.current.account_id}"

    kubeconfig = <<KUBECONFIG
      apiVersion: v1
      clusters:
      - cluster:
          certificate-authority-data: ${aws_eks_cluster.eks_master.certificate_authority.0.data}
          server: ${aws_eks_cluster.eks_master.endpoint}
        name: ${aws_eks_cluster.eks_master.arn}
      contexts:
      - context:
          cluster: ${aws_eks_cluster.eks_master.arn}
          user: ${aws_eks_cluster.eks_master.arn}
        name: ${aws_eks_cluster.eks_master.arn}
      current-context: ${aws_eks_cluster.eks_master.arn}
      kind: Config
      preferences: {}
      users:
      - name: ${aws_eks_cluster.eks_master.arn}
        user:
          exec:
            apiVersion: client.authentication.k8s.io/v1beta1
            command: aws
            args:
              - --region
              - "${var.region}"
              - eks
              - get-token
              - --cluster-name
              - "${var.building_block}-${var.env}-eks"
    KUBECONFIG

    s3_bucket              = local.storage_bucket
    s3_access_key          = aws_iam_access_key.s3_user_key.id
    s3_secret_key          = aws_iam_access_key.s3_user_key.secret
}