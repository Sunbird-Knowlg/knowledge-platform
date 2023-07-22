locals {
    common_tags = {
      Environment = "${var.env}"
      BuildingBlock = "${var.building_block}"
    }
    environment_name = "${var.building_block}-${var.env}"
   // uid = local.subid[0]
   // env_name_without_dashes = replace(local.environment_name, "-", "")
    storage_name = var.storage_s3

}