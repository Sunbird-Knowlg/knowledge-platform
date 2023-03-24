resource "aws_vpc" "vpc" {
    cidr_block       = var.vpc_cidr
    instance_tenancy = var.vpc_instance_tenancy
    tags = merge(
      {
        Name = "${var.building_block}-${var.env}-vpc"
      },
      local.common_tags,
      var.additional_tags)
}