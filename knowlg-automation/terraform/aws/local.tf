locals {
    common_tags = {
      Environment = "${var.env}"
      BuildingBlock = "${var.building_block}"
    }
    environment_name = "${var.building_block}-${var.env}"
    storagename = "mystorage421"

}