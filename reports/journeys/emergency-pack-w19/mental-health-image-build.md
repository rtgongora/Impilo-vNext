# Mental-health-service — local image build (not preview-deployed)

Built on 2026-07-30 in worktree `wt-emergency-w15-w19`. **No preview deploy was run.**

| Item | Value |
|------|--------|
| Dockerfile | `services/mental-health-service/Dockerfile` (JRE 21, port 8397) |
| Build | `mvn -pl mental-health-service -am -DskipTests package` then `bash scripts/build/build-runtime-image-from-jar.sh mental-health-service` |
| Local tags | `impilo/mental-health-service:preview`, `impilo/mental-health-service:preview-b76ece41f` |
| Image digest (local) | `sha256:b77d2efb29625975313136a1e054fdcc5bb3fca8a1825838b8288afc7274cd26` |

## Still required before claiming reachable on preview

1. Push/import image to the estate registry.
2. Run `scripts/full-boot/resolve-image-digests.sh` so a **real** digest lands in `values-full-preview-digests.generated.yaml` — never hand-write.
3. Explicit authorization: `AUTHORIZE FULL BOOT PREVIEW DEPLOY` (or the slice phrase if applicable).

Helm runtime + BFF env already list `mental-health-service` (`values-full-preview-runtime.generated.yaml`, BFF `MENTAL_HEALTH_BASE_URL`). Envoy/compose public routes remain optional while BFF is the only caller.
