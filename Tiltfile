docker_build('maheshkg/content-service:1.0', '.')
k8s_yaml('content-service.yaml')
k8s_resource('dev', port_forwards='8000')
