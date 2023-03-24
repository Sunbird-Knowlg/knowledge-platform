# resource "aws_s3_bucket_policy" "s3_bucket_policy" {
#     bucket = aws_s3_bucket.storage_bucket.id
#     policy = <<POLICY
#     {
#     "Version": "2012-10-17",
#     "Statement": [
#         {
#         "Effect": "Allow",
#         "Action": "s3:ListBucket",
#         "Resource": "arn:aws:s3:::${data.aws_s3_bucket.terraform_storage_bucket.name}"
#         },
#         {
#         "Effect": "Allow",
#         "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
#         "Resource": "arn:aws:s3:::${data.aws_s3_bucket.terraform_storage_bucket.name}/terraform.tfstate"
#         }
#     ]
#     }
#     POLICY
# }