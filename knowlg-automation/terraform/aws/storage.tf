resource "aws_s3_bucket" "s3bucket" {
  bucket = local.storagename
  tags = merge(
    local.common_tags,
    var.additional_tags
  )
}
