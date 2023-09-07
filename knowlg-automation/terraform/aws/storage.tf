resource "aws_s3_bucket" "mybucket421" {
  bucket = local.storagename
  tags = merge(
    local.common_tags,
    var.additional_tags
  )
}
