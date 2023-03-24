resource "helm_release" "postgres" {
  chart = "https://charts.bitnami.com/bitnami/postgresql-11.9.1.tgz"
  name = "postgresql"
  timeout = 600
  namespace = "postgresql"
  create_namespace = true
  //storage_class_name = var.PERSISTENT_STORAGE_CLASS

  values = [
    file("${path.module}/postgresql/custom-values.yaml")
  ]
}