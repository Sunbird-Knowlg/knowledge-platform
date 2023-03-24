resource "local_file" "kubeconfig" {
    content  = local.kubeconfig
    filename = "${var.building_block}-${var.env}-kubeconfig.yaml"
}