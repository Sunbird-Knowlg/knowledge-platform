
provider "helm" {
  kubernetes {
      config_path = var.kind_cluster_config_path
      config_context = var.kube_config_context
  }
}

resource "helm_release" "kafka" {
  name             = "kafka"
  chart              = var.KAFKA_CHART
  namespace        = var.KAFKA_NAMESPACE
  create_namespace = true
  dependency_update = true
  depends_on       = [kind_cluster.one-click]
  wait_for_jobs    = true
  values = [
    templatefile("../../helm_charts/kafka/values.yaml",
      {
        #kafka_namespace: "kafka",
        # kafka_image_repository: "bitnami/kafka"
        # kafka_image_tag: "2.8.1-debian-10-r31"
        # kafka_delete_topic_enable: true
        # kafka_replica_count: 1
        # # Kubernetes Service type for external access. It can be NodePort or LoadBalancer
        # # service_type: "ClusterIP"
        # service_type: "NodePort"
        # service_port: 9092
        # # PV config
        # kafka_persistence_size: "2Gi"
        # #Zookeeper configs
        # zookeeper_enabled: true
        # zookeeper_heapsize: 256
        # zookeeper_replica_count: 1
        content_publish_topic = "dev.publish.job.request"
        content_postpublish_topic = "dev.content.postpublish.request"
        learning_job_request_topic = "dev.learning_job_request"
        learning_graph_events_topic = "dev.learning.graph.events"
        learning_events_failed_topic = "dev.learning.events.failed"
        search_indexer_group_topic = "dev.search-indexer-group"
        qrimage_request_topic = "dev.qrimage.request"
        telemetry_raw_topic = "dev.telemetry.raw"
        dialcode_context_job_request_topic = "dev.dialcode.context.job.request"
        dialcode_context_job_request_failed_topic = "dev.dialcode.context.job.request.failed"
      }
    )
  ]
}

# data "kubernetes_service" "kafka" {
#   metadata {
#     namespace = "kafka"
#     name = "kafka"
#   }
#   depends_on = [kind_cluster.one-click, helm_release.kafka]
# }

# data "kubernetes_service" "zookeeper" {
#   metadata {
#     namespace = "kafka"
#     name = "kafka-zookeeper"
#   }
#   depends_on = [kind_cluster.one-click, helm_release.kafka]
# }

# output "kafka-service-ip" {
#   value     = data.kubernetes_service.kafka.spec.0.cluster_ip
# }

# output "zookeeper-service-ip" {
#   value     = data.kubernetes_service.zookeeper.spec.0.cluster_ip
# }
