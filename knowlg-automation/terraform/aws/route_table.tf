resource "aws_route_table" "public_route_table" {
    vpc_id = aws_vpc.vpc.id

    route {
      cidr_block = var.igw_cidr
      gateway_id = aws_internet_gateway.internet_gateway.id
    }

    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-public-route-table"
      },
      local.common_tags,
      var.additional_tags)
}