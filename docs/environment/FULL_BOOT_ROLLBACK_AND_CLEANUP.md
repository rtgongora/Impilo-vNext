# Full Boot Rollback and Cleanup

Use this when a full-boot attempt in **`impilo-full-preview`** must be recovered
without affecting the **slice** in **`impilo-preview`**.

> **Data-safety correction (2026-07):** do not uninstall this release or delete
> its namespace during routine recovery. The preview estate is data-bearing and
> uses k3s `local-path` volumes. The default recovery path is an in-place Helm
> upgrade or rollback. `scripts/operator/fullboot.sh` is the only supported
> clean-rebuild entry point; it preserves PVCs by default and requires both
> `FULL_BOOT_WIPE_DATA=1` and the typed wipe phrase before a namespace deletion.

Before any recovery:

```bash
NAMESPACE=impilo-full-preview bash scripts/full-boot/retain-data-volumes.sh
NAMESPACE=impilo-full-preview bash scripts/full-boot/verify-persistence.sh
NAMESPACE=impilo-full-preview bash scripts/full-boot/create-predeploy-backup.sh
```

## Inspect full boot namespace

```bash
kubectl get all -n impilo-full-preview
kubectl get events -n impilo-full-preview --sort-by=.lastTimestamp | tail -50
kubectl get pods -n impilo-full-preview -o wide
```

## In-place Helm rollback (preferred)

```bash
helm history impilo-full-preview -n impilo-full-preview
helm rollback impilo-full-preview <known-good-revision> \
  -n impilo-full-preview --wait --timeout 60m
```

This preserves the namespace, PVCs, TLS edge, secrets, and unchanged workloads.
After rollback, rerun the persistence, runtime-image-truth, smoke, and public-edge
gates.

## Data-preserving clean rebuild (exception only)

If an in-place recovery is impossible, use the guarded operator path:

```bash
export FULL_BOOT_CLEAN_REBUILD=1
bash scripts/operator/fullboot.sh deploy
```

This removes workloads but keeps the namespace and data PVCs. Never run
`kubectl delete namespace impilo-full-preview` manually. A true data wipe is a
separate disaster-recovery decision, not a deployment or rollback technique.

## k3s image helper (optional rollback)

Removing the helper does **not** affect running clusters; it only revokes passwordless import/list:

```bash
sudo bash scripts/operator/uninstall-k3s-image-helper.sh
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

- Do **not** `helm uninstall impilo-full-preview` or delete its namespace manually.
- Do **not** `helm uninstall impilo-preview` or delete `impilo-preview` namespace.
- Do **not** change Traefik ingress in `impilo-preview` when testing full boot.
- Do **not** use production secrets or patient data in rollback/debug commands.
