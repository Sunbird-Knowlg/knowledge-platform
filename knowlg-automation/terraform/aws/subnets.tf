resource "aws_subnet" "public_subnets" {
    count      = length(var.public_subnet_cidrs)
    vpc_id     = aws_vpc.vpc.id
    cidr_block = element(var.public_subnet_cidrs, count.index)
    availability_zone = element(local.availability_zones, count.index)
    map_public_ip_on_launch = var.auto_assign_public_ip

    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-public-subnet-${count.index}"
      },
      local.common_tags,
      var.additional_tags)
}