resource "aws_s3_bucket" "bucket" {
  bucket = "${local.storage_name}"

 tags = merge(
      local.common_tags,
      var.additional_tags
      )
}