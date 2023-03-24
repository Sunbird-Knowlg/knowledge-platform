resource "helm_release" "obsrv_superset" {
  name             = "obsrv-superset"
  chart            = "../../helm_charts/superset-helm"
  #repository       = "./helm_charts"
  namespace        = var.SUPERSET_NAMESPACE
  create_namespace = true
  depends_on       = [helm_release.obs_druid_cluster]
  wait_for_jobs    = true
  values           = [
    templatefile("../../helm_charts/superset-helm/values.yaml",
      {
#        rds_host                 = azurerm_postgresql_server.postgres.fqdn,
#        rds_superset_db_name     = var.SUPERSET_RDS_DB_NAME
#        rds_superset_db_user     = "druid@postgresql-server-obsrv"
#        rds_superset_db_password = var.SUPERSET_RDS_DB_PASSWORD
#        rds_user                 = var.RDS_USER
#        rds_password             = var.RDS_PASSWORD
#        rds_superset_port        = var.SUPERSET_RDS_PORT
        # Superset Configurations
#         supersetNode.connections.db_port = 5432
#         supersetNode.connections.db_user = "superset"
#         supersetNode.connections.db_pass = "S@nk2022"
#         supersetNode.connections.db_name = "superset"
#        admin_username           = var.SUPERSET_ADMIN_USERNAME
#        admin_firstname          = var.SUPERSET_ADMIN_FIRSTNAME
#        admin_lastname           = var.SUPERSET_ADMIN_LASTNAME
#        admin_email              = var.SUPERSET_ADMIN_EMAIL
#        admin_password           = var.SUPERSET_ADMIN_PASSWORD
      }
    )
  ]
}
