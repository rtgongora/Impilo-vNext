# Dev Preview Operations

## Cluster Health

```bash
kubectl get nodes
kubectl get pods -A
sudo systemctl status k3s
```

## App Health

```bash
bash scripts/deploy/preview-status.sh
curl -s http://41.57.127.235/actuator/health
curl -s http://41.57.127.235/health/version | jq .
```

## Logs

```bash
bash scripts/deploy/preview-logs.sh experience-bff
bash scripts/deploy/preview-logs.sh one-ui-shell
kubectl logs -n impilo-preview -l app=experience-bff --tail=100
```

## Redeploy

```bash
bash scripts/deploy/preview-build-images.sh
bash scripts/deploy/preview-deploy.sh
```

## Rollback

```bash
bash scripts/deploy/preview-rollback.sh
```

## Deployed Commit

```bash
cd /opt/impilo/repos/Impilo-vNext && git rev-parse HEAD
curl -s http://41.57.127.235/health/version
```

## Resource Checks

```bash
free -h
df -h /
kubectl top nodes 2>/dev/null || true
```

## Restart k3s

```bash
sudo systemctl restart k3s
```

## Failed Pods

```bash
kubectl get pods -n impilo-preview
kubectl describe pod -n impilo-preview <name>
```
