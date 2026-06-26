# Facility Mode — Ownership Split (T1 ENTERS · T4 BUILDS)

> **Status:** DESIGN GATE — decisive ownership doc. Branch `intake/provider-clinical-place-design`.
> Resolves the #1 collision: Facility Mode is half-built and both the Provider lane (T1) and the
> Facility/Place lane (T4) will reach for the same files. **There is exactly one Facility Mode.** This doc
> assigns every file/route to exactly one owner so no duplicate Facility Mode is ever created.

## 1. The principle

> **T1 ENTERS Facility Mode. T4 BUILDS Facility Mode.**

- **Facility Mode is a place/institution capability** → its system-of-record is **TUSO** (facility) +
  **Indawo** (public-health place), and its experience is **owned by the T4 facility-mode lane**.
- **The Provider experience (T1) is a consumer.** A provider *selects* a facility, *enters* Facility Mode,
  and *operates within* the cockpit T4 builds. T1 never defines the cockpit's contents, setup, or
  configuration — it only triggers entry and renders inside it via the frozen contract.

The boundary is the **`FacilityModeContext`** read-model (frozen in
[shared read-models](shared-read-models.md) § Facility/place). T1 reads it; T4 produces it.

## 2. What exists today (verified)

| Artifact | Path | Today's state | Future owner |
|----------|------|---------------|--------------|
| Shell-mode type | `ui/one-ui-shell/src/lib/identity-context.ts` (`ShellMode="facility_mode"`) | Live (state only) | **shared** (T1 reads, see §5) |
| Session contract hook | `ui/one-ui-shell/src/hooks/useSessionExperienceContract.ts` (`facilityModeActive`) | Live | **shared** (T1 reads) |
| Facility selection page | `ui/one-ui-shell/src/app/facility/page.tsx` (+ `WorkplaceSelectionHub`) | Live | **T1 (enter)** |
| BFF contract flags | `experience-bff` `SessionExperienceService` (`facilityModeAvailable`/`facilityModeActive`) | Live | **shared producer** (CZO-adjacent; see §6) |
| Facility name resolver | `experience-bff` `FacilityNameResolver` | Fixture (seeded) | **T4** (wire to TUSO) |
| Facility cockpit / home | — | **Missing** | **T4 (build)** |
| Facility setup wizard | — | **Missing** | **T4 (build)** |
| Facility ops/control-tower UI | — (`tuso` `ControlTowerController` backend Partial) | **Missing UI** | **T4 (build)** |
| Facility admin (departments/service-points) UI | — (`tuso` entities Live, no API/UI) | **Missing** | **T4 (build)** |

## 3. Ownership table — files & routes

### 3.1 OWNED BY T4 (facility/place lane builds these — net new)

**Backend (TUSO, facility SoR — migrations from V012):**
- `tuso-service/.../api/controller/FacilityUnitController.java` (NEW — expose `FacilityUnitEntity`)
- `tuso-service/.../api/controller/ServicePointController.java` (NEW — service-points/queues config)
- `tuso-service/.../core/FacilitySetupService.java` (NEW — setup-wizard orchestration: dept→service-point→
  queue→workflow→workforce→OROS-routing→Khuluma-channel→Fundo-readiness→go-live)
- `tuso-service/.../api/controller/FacilityModeController.java` (NEW — produces `FacilityModeContext`
  read-model the shell consumes)
- extend `ControlTowerController` / `ControlTowerService` for real-time aggregation (existing, complete it)

**Backend (Indawo, place SoR — migrations from V007):**
- `indawo-service/.../api/SiteModeController.java` (NEW — Indawo "place mode" cockpit context)
- `indawo-service/.../core/SurveillanceService.java` + outbreak/case/field-team entities (NEW)

**BFF (orchestration, stateless):**
- `experience-bff/.../controller/FacilityModeController.java` (NEW — composes TUSO cockpit + Indawo place mode;
  reuses `FacilityOperationsAggregateController`, `AggregateVisibilityGuard`)
- wire `FacilityNameResolver` → real TUSO lookup (replace seeded fixture)

**Web (one-ui-shell — the cockpit & wizard):**
- `ui/one-ui-shell/src/app/facility/[facilityId]/cockpit/page.tsx` (NEW — facility home/overview/quick-actions)
- `ui/one-ui-shell/src/app/facility/[facilityId]/setup/**` (NEW — setup wizard steps)
- `ui/one-ui-shell/src/app/facility/[facilityId]/control-tower/page.tsx` (NEW — scoped ops dashboard)
- `ui/one-ui-shell/src/app/facility/[facilityId]/departments/**`, `/service-points/**` (NEW — admin)
- `ui/one-ui-shell/src/app/indawo/**` (NEW — public-health place mode: risk/surveillance/inspection/outbreak/field-teams)
- `ui/one-ui-shell/src/components/facility-mode/**` (NEW — cockpit components, owned by T4)

**Mobile:** `apps/mobile/provider-app/.../screens/facility/**` facility-admin screens (T4 builds; today only a
stub `appStore.ts` facility reference exists).

### 3.2 OWNED BY T1 (provider lane — entry & operation only)

- `ui/one-ui-shell/src/app/facility/page.tsx` — facility **selection/entry** hub (exists, T1 owns).
- `ui/one-ui-shell/src/components/WorkspaceSwitcher.tsx` — person↔professional↔shift + facility-mode toggle
  (exists; T1 extends with dept/ward/service-point dimensions, see context-picker work).
- The **trigger** that flips `ShellMode → facility_mode` and routes into T4's cockpit (T1 owns the entry
  transition; T4 owns the destination).
- Provider check-in into the selected facility (`vashandi` check-in, T1/provider lane).

T1 **must not** create any `facility/[facilityId]/cockpit|setup|control-tower|departments` route or any
`facility-mode/**` component — those are T4's. If T1 needs data inside the cockpit, it consumes
`FacilityModeContext`; it does not add to T4's pages.

### 3.3 SHARED — single producer, many readers (no forking)

| Artifact | Single producer | Readers |
|----------|-----------------|---------|
| `ShellMode` type + `identity-context.ts` | (existing; edits coordinated — small, additive only) | T1, T4 |
| `useSessionExperienceContract` (`facilityModeActive/Available`) | existing hook | T1, T4 |
| `SessionExperienceService` BFF flags | existing (CZO-adjacent — see §6) | T1, T4 |
| `FacilityModeContext` read-model | **T4** (`FacilityModeController`) | T1 |

## 4. Sequence — who does what at runtime

```mermaid
sequenceDiagram
    participant T1 as T1 Provider lane (ENTER)
    participant Shell as Shell state (shared)
    participant T4BFF as T4 BFF FacilityModeController
    participant TUSO as TUSO/Indawo (SoR)
    T1->>Shell: provider selects facility (/facility page)
    T1->>Shell: flip ShellMode -> facility_mode
    Shell->>T4BFF: GET FacilityModeContext(facilityId)
    T4BFF->>TUSO: aggregate cockpit data (scoped, tenant-guarded)
    TUSO-->>T4BFF: facility ops + setup state + place mode
    T4BFF-->>Shell: FacilityModeContext
    Shell->>T1: render T4 cockpit route (/facility/{id}/cockpit)
    Note over T1,T4BFF: T1 rendered the entry; T4 owns the cockpit it lands in
```

## 5. Anti-duplication rules (enforced in review)

1. **One cockpit route prefix:** all facility-mode pages live under `app/facility/[facilityId]/**` and
   `app/indawo/**`. No second facility-home anywhere (e.g. not under `/work/facility` for the cockpit — the
   existing `/work/facility/[facilityId]/staff-access` is staff-access admin, not the cockpit; keep distinct).
2. **One component namespace:** facility-mode components live in `components/facility-mode/**` (T4). T1 reuses
   `WorkspaceSwitcher`/`ContextRail`; it does not copy them into a facility namespace.
3. **One read-model:** `FacilityModeContext` has a single producer (T4 `FacilityModeController`). T1 never
   assembles facility cockpit data itself.
4. **No facility persistence in the BFF** (stateless). Facility config persists in TUSO; place config in Indawo.
5. **Setup wizard writes only to TUSO/Indawo** via `FacilitySetupService` — never to the BFF, never to PCT.

## 6. Collision with live sessions

- `SessionExperienceService` and the auth/session contract surface are **adjacent to the live CZO auth
  cluster** (`task_430f1240` + WS sessions). The `facilityModeAvailable/Active` flags already exist and are
  **not** PolicyEngine — but any change to session/identity contracts must be coordinated with CZO and must
  **not** edit `PolicyEngine.java`, `ExtAuthzGrpcService.java`, or `AuthorizeController.java`. For Facility
  Mode, **prefer additive read-model production in TUSO + a new BFF `FacilityModeController`** over touching
  the shared session contract.
- Facility-mode authorization rules (who may enter facility mode, who may run setup, cross-tenant guards) are
  **specified, not authored**, in the [Tshepo policy contract list](tshepo-policy-contract-list.md)
  (`FACILITY-MODE-*`, `FACILITY-SETUP-*`, `XTENANT-*`). They route through WS-OPA (`impilo.authz`) or queue
  to the CZO lead.

## 7. Definition of done for the split

- T4 facility-mode lane owns: TUSO cockpit/setup/service-point controllers, Indawo place-mode + surveillance,
  BFF `FacilityModeController`, all `facility/[facilityId]/**` + `indawo/**` web routes, `facility-mode/**`
  components, facility-admin mobile screens.
- T1 provider lane owns: facility selection/entry, `ShellMode` flip, context-picker extension, check-in.
- Zero duplicate cockpit/home routes; one `FacilityModeContext` producer; no BFF persistence; facility-mode
  policy specced not authored.
