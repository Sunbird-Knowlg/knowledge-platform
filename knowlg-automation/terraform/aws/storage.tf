resource "aws_s3_bucket" "mybucket" {
  bucket = local.storagename
  tags = merge(
    local.common_tags,
    var.additional_tags
  )
}
