resource "aws_vpc" "vpc" {
  cidr_block           = var.vpc_cidr
  instance_tenancy     = "default"
  enable_dns_hostnames = true
  tags = merge(
    local.common_tags,
    var.additional_tags
  )
}

resource "aws_internet_gateway" "gateway" {
  vpc_id = aws_vpc.vpc.id
  tags = merge(
    local.common_tags,
  var.additional_tags)
}

resource "aws_eip" "nat" {
  domain = "vpc" 
  tags = {
    Name = "nat"
  }
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public-us-west-1a.id

  tags = {
    Name = "nat"
  }

  depends_on = [aws_internet_gateway.gateway]
}
resource "aws_route_table" "route_table_private" {
  vpc_id = aws_vpc.vpc.id
  route {
    cidr_block = var.destination_cidr
    nat_gateway_id = aws_nat_gateway.nat.id
  }
  tags = merge(
    local.common_tags,
  var.additional_tags)

}
resource "aws_route_table" "route_table_public" {
  vpc_id = aws_vpc.vpc.id
  route {
    cidr_block = var.destination_cidr
    gateway_id = aws_internet_gateway.gateway.id
  }
  tags = merge(
    local.common_tags,
  var.additional_tags)

}

resource "aws_route_table_association" "private-us-west-1a" {
  subnet_id      = aws_subnet.private-us-west-1a.id
  route_table_id = aws_route_table.route_table_private.id
}

resource "aws_route_table_association" "private-us-west-1b" {
  subnet_id      = aws_subnet.private-us-west-1b.id
  route_table_id = aws_route_table.route_table_private.id
}

resource "aws_route_table_association" "public-us-west-1a" {
  subnet_id      = aws_subnet.public-us-west-1a.id
  route_table_id = aws_route_table.route_table_public.id
}

resource "aws_route_table_association" "public-us-west-1b" {
  subnet_id      = aws_subnet.public-us-west-1b.id
  route_table_id = aws_route_table.route_table_public.id
}

resource "aws_subnet" "private-us-west-1a" {
  vpc_id            = aws_vpc.vpc.id
  cidr_block        = "10.0.0.0/19"
  availability_zone = "us-west-1a"

  tags = {
    "Name"                            = "private-us-west-1a"
    "kubernetes.io/role/internal-elb" = "1"
   
  }
}

resource "aws_subnet" "private-us-west-1b" {
  vpc_id            = aws_vpc.vpc.id
  cidr_block        = "10.0.32.0/19"
  availability_zone = "us-west-1b"

  tags = {
    "Name"                            = "private-us-west-1b"
    "kubernetes.io/role/internal-elb" = "1"
   
  }
}

resource "aws_subnet" "public-us-west-1a" {
  vpc_id                  = aws_vpc.vpc.id
  cidr_block              = "10.0.64.0/19"
  availability_zone       = "us-west-1a"
  map_public_ip_on_launch = true

  tags = {
    "Name"                       = "public-us-west-1a"
    "kubernetes.io/role/elb"     = "1"
    
  }
}

resource "aws_subnet" "public-us-west-1b" {
  vpc_id                  = aws_vpc.vpc.id
  cidr_block              = "10.0.96.0/19"
  availability_zone       = "us-west-1b"
  map_public_ip_on_launch = true

  tags = {
    "Name"                       = "public-us-west-1b"
    "kubernetes.io/role/elb"     = "1"
   
  }
}