# provider "docker" {
#   host = "unix:///var/run/docker.sock"
# }


# # Create a docker image resource
# resource "docker_image" "taxonomy-service" {
#   name = "taxonomy-service"
#   build {  
#     tag        = ["taxonomy-service:R5.2.0"]
#     path = "../taxonomy-service"
#     dockerfile = "../taxonomy-service"
#   }
# }
provider "kind" {
}

resource "kind_cluster" "one-click" {
  name            = var.kind_cluster_name
  kubeconfig_path = pathexpand(var.kind_cluster_config_path)
  wait_for_ready  = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      kubeadm_config_patches = [
        "kind: InitConfiguration\nnodeRegistration:\n  kubeletExtraArgs:\n    node-labels: \"ingress-ready=true\"\n"
      ]
      extra_port_mappings {
        container_port = 80
        host_port      = 80
      }
      extra_port_mappings {
        container_port = 443
        host_port      = 443
      }
    }

    node {
      role = "worker"
      kubeadm_config_patches = [
        "kind: InitConfiguration\nnodeRegistration:\n  kubeletExtraArgs:\n    node-labels: \"worker-node=true\"\n" 
      ]
    }

    node {
      role = "worker"
      kubeadm_config_patches = [
        "kind: InitConfiguration\nnodeRegistration:\n  kubeletExtraArgs:\n    node-labels: \"worker-node=true\"\n"
      ]
    }

    node {
      role = "worker"
      kubeadm_config_patches = [
        "kind: InitConfiguration\nnodeRegistration:\n  kubeletExtraArgs:\n    node-labels: \"worker-node=true\"\n"
      ]
    }
  }

}



# resource "docker_image" "taxonomy-service" {  
#   name = "taxonomy-service"
#   build {
#     context = "https://github.com/Sunbird-Knowlg/knowledge-platform.git#master:build/taxonomy-service/Dockerfile"
#     tag     = ["taxonomy-service:R5.2.0"]
#     build_arg = {
#       foo : "taxonomy-service"
#     }
#     label = {
#       author : "taxonomy-service"
#     }
#   }
# }

