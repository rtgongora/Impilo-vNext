# Full Boot Rollback and Cleanup

Use this when a full-boot attempt in **`impilo-full-preview`** must be torn down without affecting the **slice** in **`impilo-preview`**.

## Inspect full boot namespace

```bash
kubectl get all -n impilo-full-preview
kubectl get events -n impilo-full-preview --sort-by=.lastTimestamp | tail -50
kubectl get pods -n impilo-full-preview -o wide
```

## Uninstall Helm release (preferred rollback)

```bash
helm uninstall impilo-full-preview -n impilo-full-preview
```

This removes chart-managed workloads in `impilo-full-preview` only. It does **not** modify `impilo-preview`.

## Delete namespace (full cleanup)

Only when you need a completely empty full-boot namespace:

```bash
helm uninstall impilo-full-preview -n impilo-full-preview 2>/dev/null || true
kubectl delete namespace impilo-full-preview
```

Wait until gone:

```bash
kubectl get namespace impilo-full-preview
```

## Verify slice preserved

```bash
kubectl get all -n impilo-preview
helm list -n impilo-preview
curl -s http://41.57.127.235/health/version
```

Expected: four slice pods (postgres, redis, experience-bff, one-ui-shell) Running; public health/version still responds.

## Preserve logs before cleanup

```bash
NS=impilo-full-preview
kubectl get events -n "$NS" --sort-by=.lastTimestamp | tee /tmp/full-boot-events.txt
for p in $(kubectl get pods -n "$NS" -o name); do
  kubectl logs -n "$NS" "$p" --all-containers > "/tmp/${p//\//-}.log" 2>&1 || true
done
```

## Re-attempt after cleanup

1. Import and verify images on the VM:

```bash
sudo -v
bash scripts/dev/import-full-vnext-images-k3s.sh preview
NS=impilo-full-preview bash scripts/dev/verify-full-boot-k3s-images.sh preview
# Gate on IMAGE_PRESENCE: PASS and SUMMARY ok=22 fail=0 (not v-* pod Running alone)
```

2. `bash scripts/deploy/full-boot-preview-deploy.sh --preflight`
3. `bash scripts/deploy/full-boot-preview-deploy.sh --dry-run`
4. Authorized deploy with `bash scripts/deploy/full-boot-preview-deploy.sh`

## What NOT to do

- Do **not** `helm uninstall impilo-preview` or delete `impilo-preview` namespace.
- Do **not** change Traefik ingress in `impilo-preview` when testing full boot.
- Do **not** use production secrets or patient data in rollback/debug commands.
