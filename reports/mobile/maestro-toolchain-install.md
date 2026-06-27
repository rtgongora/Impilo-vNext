# Maestro Toolchain Install

**Host:** `41.57.127.235` (static closure / export validation only)  
**Maestro VM (218):** **NOT INSTALLED** — agent could not SSH to 218  
**Date:** 2026-06-27

## 235 (engineering control) — tools used

| Tool | Version / status |
|------|------------------|
| git | available |
| node | available (system) |
| npx pnpm@9.15.0 | used for mobile workspace |
| Java / Android SDK | **not installed** (by design — no emulator on 235) |

## 218 (Maestro) — planned install

See `scripts/mobile/maestro-vm-bootstrap.sh`:

- git, curl, ca-certificates, gnupg, unzip, zip, build-essential
- openjdk-17-jdk
- Node.js 20 LTS (NodeSource)
- corepack + pnpm 9.15.0
- Maestro CLI (via runtime script / manual)

## Status

| VM | Toolchain |
|----|-----------|
| 235 | Node + pnpm for static gates only |
| 218 | **PENDING** — run bootstrap on 218 |
