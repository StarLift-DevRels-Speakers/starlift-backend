# Kubernetes deployment

Manifests in this directory describe a production-like deployment for the Java Speaker Club backend:

- Spring Boot backend as a `Deployment` with rolling updates, probes, resource limits and non-root runtime.
- PostgreSQL, MinIO and Redis as `StatefulSet` workloads with persistent volumes.
- `ConfigMap` and `Secret` separation for application configuration.
- Internal `ClusterIP` services and an nginx `Ingress`.
- KEDA autoscaling for the backend by CPU, memory and a demonstration Prometheus metric for PVC disk usage.

Apply order:

```sh
kubectl apply -f k8s/
```

Before a real deployment, replace demo values in `02-secrets.yaml`, set the backend image in `06-app.yaml` to the registry image, and verify that the cluster has:

- a default `StorageClass`;
- nginx ingress controller;
- metrics-server;
- KEDA installed;
- Prometheus exposing `kubelet_volume_stats_used_bytes` and `kubelet_volume_stats_capacity_bytes`.

The disk autoscaling rule in `08-autoscaling-keda.yaml` is intentionally modeled as an external metric. Kubernetes does not scale pods by disk usage by itself; KEDA reads the Prometheus query and creates/updates the HPA.
