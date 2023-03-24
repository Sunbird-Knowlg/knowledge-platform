resource "aws_s3_bucket" "storage_bucket" {
    bucket = local.storage_bucket

    tags = merge(
      {
        Name = local.storage_bucket
      },
      local.common_tags,
      var.additional_tags)
}