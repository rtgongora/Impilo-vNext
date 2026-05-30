# Image Build Strategy (Dev Preview)

## Selected Approach

**Local Docker build → import into k3s containerd**

Simplest reliable path for single-node preview without registry credentials.

## Tools

- Docker Engine (build)
- `docker save | sudo k3s ctr images import -` (load into cluster)

## Build Commands

```bash
bash scripts/dev/build-images.sh
# or
bash scripts/deploy/preview-build-images.sh
```

## Tagging Convention

| Tag | Meaning |
|-----|---------|
| `preview` | Current preview deploy |
| `preview-<short-sha>` | Commit-specific tag |

Helm values: `images.*.tag: preview`, `imagePullPolicy: IfNotPresent`

## k3s Availability

Images must be imported on the same node running k3s. No pull from external registry required for MVP.

## Limitations

- No shared registry across nodes (single-node only)
- Re-import required after each rebuild
- Not suitable for formal staging/production

## Future Recommendation

Private registry (GHCR or Harbor) with GitHub Actions push + Helm image pull secrets for formal environments.
