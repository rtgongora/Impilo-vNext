# Dependency Installation Report

**Host:** `user-HVM-domU` (41.57.127.235)  
**Repo:** `/opt/impilo/repos/Impilo-vNext`  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Last updated:** 2026-05-30 (automated setup)

## Tool Versions Installed

| Tool | Version |
|------|---------|
| Java | OpenJDK Temurin 21 |
| Maven | 3.x (Ubuntu package) |
| Node.js | 20 LTS |
| npm | 10.x |
| Docker | 29.5.2 |
| k3s | v1.35.5+k3s1 |
| Helm | v3.21.0 |

## Package Manager

- **Frontend:** npm (root `ui/package-lock.json`, `packageManager: npm@10.8.2`)
- **Backend:** Maven (`services/pom.xml` reactor)
- **Mobile (optional):** pnpm — not installed by default (`INSTALL_MOBILE=1` to enable)

## Commands

```bash
bash scripts/dev/install-dependencies.sh   # mvn install -pl experience-bff -am; npm ci in ui/
bash scripts/dev/build-all.sh              # mvn package; npm build one-ui-shell
```

## Results (initial setup)

| Step | Status | Notes |
|------|--------|-------|
| Base VM tools | PASS | git, Java, Maven, Node, Docker, k3s, Helm |
| Maven `-pl experience-bff -am` | **In progress / retry** | Initial attempts failed due to incorrect `-pl` module paths; fixed in scripts |
| npm `ui/` install | Pending | Runs after Maven fix in same script |
| Mobile pnpm | Skipped | Not required for preview MVP |

## Blockers Resolved

1. **sudo password** — bootstrap uses `sudo -S -v` with `SUDO_PASS` (non-interactive).
2. **Corrupted adoptium.list** — removed and recreated.
3. **Maven reactor** — use `mvn install -pl experience-bff -am` from `services/`.

## Refresh Report

```bash
bash scripts/dev/write-dependency-report.sh
```
