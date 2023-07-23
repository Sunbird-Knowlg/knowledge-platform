resource "aws_vpc" "vpc" {
  cidr_block = var.vpc_cidr
  instance_tenancy = "default"
  enable_dns_hostnames    = true
  tags = merge(
    local.common_tags,
    var.additional_tags
  )
}

resource "aws_subnet" "eks_subnet" {
  vpc_id     = aws_vpc.vpc.id
  cidr_block = var.subnet_cidr
  availability_zone = data.aws_availability_zones.zones_available.names[0]
  map_public_ip_on_launch = true
  tags = merge(
    local.common_tags,
    var.additional_tags)
}

resource "aws_internet_gateway" "gateway"{
  vpc_id = aws_vpc.vpc.id
  tags = merge(
    local.common_tags,
    var.additional_tags)
}

resource "aws_route_table" "route_table" {
  vpc_id = aws_vpc.vpc.id
  route{
    cidr_block = var.destination_cidr
    gateway_id = aws_internet_gateway.gateway.id
  }
  tags = merge(
    local.common_tags,
    var.additional_tags)

}

resource "aws_route_table_association" "my_association" {
  subnet_id      = aws_subnet.eks_subnet.id
  route_table_id = aws_route_table.route_table.id
}
