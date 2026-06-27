# vNext Environment Ladder

**Doctrine:** Provision and operate the full target pipeline as one coordinated setup. Environments may be activated progressively, but **names, networks, access model, operator entrypoints, and validation path** are established here.

> **Dual-VM note:** Steps 1–2 are **active today**. Steps 3–11 are named targets in the full pipeline; do not treat throwaway ad-hoc hosts as substitutes.

Full topology: [`VNEXT_ENVIRONMENT_TARGET_TOPOLOGY.md`](./VNEXT_ENVIRONMENT_TARGET_TOPOLOGY.md) · Promotion gates: [`VNEXT_PROMOTION_GATES.md`](./VNEXT_PROMOTION_GATES.md)

---

## 1. impilo-web-preview / engineering control — **ACTIVE**

| Item | Value |
|------|-------|
| **Host** | `41.57.127.235` |
| **SSH** | `ssh -p 2276 robert@41.57.127.235` |
| **Repo** | `/opt/impilo/repos/Impilo-vNext` |
| **Preview / API** | `http://41.57.127.235` |
| **Branch** | `claude/staging-ux-orchestration-remediation-Yypyl` |

**Role:** Canonical engineering control workspace; Cursor primary workspace; web preview; backend/API preview readiness; k3s preview deploy; quality gates; operator scripts; branch source-of-truth operations.

**Does not run here:** Android emulator load, Maestro runtime smoke.

---

## 2. impilo-mobile-preview-control — **PLANNED**

**Role:** Mobile preview orchestration layer — coordinates mobile build variants, preview API routing, and handoff to Android/iOS sandboxes. May colocate with web-preview control initially; separate control plane when mobile preview traffic warrants it.

**API target (initial):** `http://41.57.127.235`

---

## 3. impilo-mobile-android-sandbox / MOHCC Maestro VM — **ACTIVE**

| Item | Value |
|------|-------|
| **Host** | `41.57.127.218` |
| **Hostname** | `ministryofhealth-HVM-domU` |
| **SSH** | `ssh facility@41.57.127.218 -p 2027` |
| **Repo** | `/opt/impilo/repos/Impilo-vNext` (sync via Git) |
| **API target** | `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235` |

**Role:** Android emulator automation; mobile runtime validation; citizen/provider smoke; APK install/build validation; screenshots/logcat/runtime evidence; mobile runtime reports.

**Does not run here:** Backend deploy, web preview, full integration stack, production simulation, production.

Runbook: [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../mobile/MOBILE_ANDROID_SANDBOX.md)

---

## 4. impilo-mobile-ios-sandbox / EAS path — **PLANNED**

**Role:** iOS runtime validation via EAS Build / TestFlight-style paths. **Not** native iOS builds on Ubuntu Maestro VM — do not claim otherwise.

---

## 5. impilo-web-test-sandbox — **PLANNED**

**Role:** Dedicated web regression / Playwright / compose E2E beyond dev preview slice.

---

## 6. impilo-cross-surface-test-controller — **PLANNED**

**Role:** Coordinates web + mobile + BFF cross-surface journeys; consumes preview and sandbox APIs.

---

## 7. impilo-full-integration-sandbox — **PLANNED**

**Role:** Full vNext service mesh / full-boot validation (`impilo-full-preview` namespace doctrine). Not the Maestro VM.

---

## 8. impilo-production-simulation-lab — **PLANNED**

**Role:** Production-like load, failover, and ops drills. **Not** the Maestro VM or web preview VM.

---

## 9. impilo-staging / pre-production — **PLANNED**

**Role:** Formal pre-production gate before production promotion. See [`FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md`](./FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md).

---

## 10. impilo-production — **PLANNED**

**Role:** Sovereign production Health OS runtime. Out of scope for current dev-test VMs.

---

## 11. shared platform services — **PLANNED**

**Role:** Cross-environment shared services (identity, observability backbone, artifact registry, secrets vault policy). Named now; provisioned with formal staging/production.

---

## Active dual-VM summary

| Ladder step | IP | Status |
|-------------|-----|--------|
| impilo-web-preview | 41.57.127.235 | Active |
| impilo-mobile-android-sandbox | 41.57.127.218 | Active (KVM validated; toolchain pending) |
| Steps 2, 4–11 | TBD | Named; progressive activation |

Historical correction: `41.57.127.218` was once documented as retired — **superseded 2026-06-27**. See [`VM_BASELINE_AUDIT.md`](./VM_BASELINE_AUDIT.md).
