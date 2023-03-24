resource "aws_internet_gateway" "internet_gateway" {
   vpc_id = aws_vpc.vpc.id

    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-igw"
      },
      local.common_tags,
      var.additional_tags)
}