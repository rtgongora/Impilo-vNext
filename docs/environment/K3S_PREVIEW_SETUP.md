# k3s Dev Preview Setup

## Installation

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--write-kubeconfig-mode 644" sh -
mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
```

## Helm

```bash
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

## Namespaces

- `impilo-preview` — application preview
- `impilo-infra` — reserved for shared infra later
- `impilo-observability` — reserved

## Storage

k3s default: `local-path` StorageClass. MVP Postgres uses `emptyDir` (non-durable — preview only).

## Ingress

Default **Traefik** (k3s bundled). Preview ingress routes:

- `/` → one-ui-shell
- `/internal`, `/actuator`, `/health`, `/auth` → experience-bff

## Verification Commands

```bash
kubectl get nodes
kubectl get pods -A
kubectl get storageclass
helm version
kubectl get pods -n impilo-preview
```

## Known Limitations

- Single node — no HA
- No TLS on HTTP preview (IP access only)
- Kafka/Keycloak not in MVP chart
