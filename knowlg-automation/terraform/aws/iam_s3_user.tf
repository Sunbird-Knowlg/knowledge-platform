resource "aws_iam_user" "s3_user" {
  name          = "s3_user"
  path          = "/"
}

resource "aws_iam_access_key" "s3_user_key" {
  user = aws_iam_user.s3_user.name
  depends_on = [aws_iam_user.s3_user]
}