# Full Implementation Wave — Website + vNext Outstanding Items

> **Wave window:** 2026-07-10 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` · **Final HEAD:** `43c56b488`
> **Mission (PO):** "Demo is over. Can we now do the full implementation for all the outstanding website and vNext issues. I notice you missed the Ndila work which was done by Cursor and the map files we uploaded onto the VM."

## P1 — Ndila street stack (Cursor's work, landed + made true)

Cursor authored the self-hosted street-map stack; landing it surfaced **two dead-by-construction defects** that would have shipped a broken map:

| Defect | Truth | Fix |
| --- | --- | --- |
| `martin` launched with `--pmtiles-path` | Flag does not exist in martin v0.15 (positional `CONNECTION` arg only) → crash-loop | `args: ["/data"]` in helm (`4e4076cf8`) and compose (`4b7cf60bb`) |
| Raster proxy forwarded martin bytes as `image/png` | Martin serves **gzipped MVT vector protobuf** — browsers rendered broken tiles, *worse* than the sovereign fallback | PNG-sniff on the raster path (falls back to sovereign preview raster) + governed `.mvt` vector passthrough ndila→BFF + native MapLibre vector rendering with an OpenMapTiles street layer set (`781e0231c`, `e54a304be`, `115fd4f04`) |
| MapLibre tile fetches 400-ed at the BFF | transformRequest sent only `Authorization`; the v1.1 header filter rejects every tile | Synthesize `X-Tenant-ID/X-Pod-ID/X-Request-ID/X-Correlation-ID` exactly as api-client does (`4913ad6c4`) |

**Deployed + live-proven:** `ndila-martin` (PVC `ndila-martin-data`, `zimbabwe.pmtiles` 123 MB) serves real Zimbabwe tiles (Harare z10 = 35 KB MVT); chain `BFF → ndila-service → martin` returns 200 protobuf with correct `Content-Encoding`, and the `.png` path returns the honest 7 KB sovereign preview PNG. `tiles/config` advertises `vectorTileUrlTemplate` when the street stack is live. Street-stack env applied to the live `ndila-service` matching the committed generator wiring. Martin resources adopted into the Helm release (they had been `kubectl apply`-ed without ownership metadata — caught by the fullboot dry-run, fixed).

**Honest gap:** the street style is **label-free** — no glyphs endpoint exists yet, and a MapLibre symbol layer without glyphs hard-fails the style. Follow-up: serve a font glyph pyramid (martin `--font` + BFF passthrough) and add place/road label layers.

## P2 — Public website finishing pass

- **NHOS identity sweep:** zero remaining "digital health ecosystem" identity phrasing (About, Services, Resources, Get Involved, metas/keywords). Brand statement live on the hero and footer.
- **Performance:** hero photo 3 MB PNG → 44 KB WebP (1200 px); MOHCC navbar logo 480 KB → 12 KB palette PNG. Remaining heavy assets are the four downloadable `.docx` documents (intentional content).
- **Deployed:** container rebuilt from the new dist and rolled; https://impilo.mohcc.gov.zw verified serving the new bundle (`index-Dv4UBrXX.js`). Pushed to both `zimttech/impilo-website` and `rtgongora/impilo-website` (branch `public-gateway-vnext-handoff`, tip `d9ec6a8` + report refresh).
- **Upstream reconciliation** documented in the refactor report: GitHub `main` is ~4 commits of undeployed "ecosystem transform" ahead of what production actually served; merging/retiring it is an explicit team decision.

## P3 — Nhume live write-backs + remaining deferrals

### Live owning-service write-backs on drop-off sign-off (`8bc77e6de`, `f645e380e`)
When a mission transitions to `DELIVERED`, nhume-service now confirms the movement with the services that own the cargo truth, via the `metadata.links` refs captured at mission creation:

| System | Write-back | Notes |
| --- | --- | --- |
| OROS | in-flight specimens of the linked lab order received into the lab | lists specimens, receives `DISPATCHED`/`IN_TRANSIT` only |
| MADI | blood order completed | **new** `ISSUED → COMPLETED` transition + endpoint added to MADI (its own SoR lifecycle was missing the last step) |
| PCT | referral package accepted on arrival | |
| Dura | **honestly skipped** (`SKIPPED_NO_TRANSITION`) | requisition lifecycle ends at `FULFILLED` (warehouse-side); no destination-receipt transition exists |

Failure is a first-class state: outcomes (including failures) are merged into `metadata.links_writeback`, emitted to the outbox (`nhume.delivery.integration_writeback.v1`), audited, and a failed write-back raises a WARNING delivery exception — but **never blocks the courier-facing sign-off**. The signing actor's bearer is propagated for honest attribution; idempotency keys are stable per (delivery, action). The delivery link panel surfaces the outcomes as chips (Confirmed with owner / Confirmation failed / Manual step in owner).

### Daidzai escalation is real (`f907a9c5b`, `6c9cd2014`)
The escalate deep-link now lands on a prefilled incident form; creating it POSTs a genuine Daidzai incident and writes `daidzaiIncidentRef` back onto the delivery via a new whitelisted `POST /deliveries/{id}/links` endpoint (audited; honest partial state shown when the link-back fails).

### Dispatch persona truth (realm + seeds + gates)
`DISPATCH_COORDINATOR` and `COURIER` realm roles; `dispatcher.chirwa` and `courier.banda` personas (seeded and **verified live** by the persona pack orchestrator); `DISPATCH_OPERATIONS` role group now gates Dispatch Ops — no dead gates, and coordinator joins the operations aggregate.

### Mobile drop-off honesty (`d6c9cef4`-family)
The courier proof screen said "marked as delivered" but never sent `mark_delivered` — the mission never closed. All three proof methods now sign off the `DELIVERY` stage with `mark_delivered: true`.

### Trust Console invitations tab
Fifth IATG queue composing workforce-governance import rows with their invitation lifecycle (sent/expired/failed actionable; activated/revoked drop out). `RESEND`/`REVOKE` route to the established per-row actions; wgv downtime degrades this queue only (`pending_backend`), never the console.

### Governance-intake golden journey + registry fix
Two-persona spec (claim → IATG review across all five queues, honest-state assertions). `services-registry.yaml` now records nhume's real port 8210 with the data-ingestion collision note.

## Verification

| Gate | Result |
| --- | --- |
| UI vitest | **1579/1579** |
| UI routes | 0 missing pages |
| Launcher dead-end check | PASS |
| experience-bff | **866/866** |
| nhume-service | **23/23** |
| madi-service | **31/31** |
| Mobile provider-app tsc | clean |
| Website lint + build | clean, live bundle verified |
| Fullboot `prepare` | CHART_INTEGRITY_PASS · PREFLIGHT_PASS · dry-run clean (after martin Helm adoption) · estate 97/97, helm 22/22 |

## Deployment state

Targeted rolls (accelerated method) at `43c56b488`: `ndila-service`, `experience-bff`, `one-ui-shell`, `nhume-service`, `madi-service`, `public-website` — all gated rollouts green, ingress + service health 200. Personas seeded (8 provider + 7 governance/citizen). **Certified fullboot at final HEAD is prepared and held for PO authorization** (helm state is otherwise drifted by the targeted rolls; `/health/version` stays stale until fullboot).

## Golden journeys (live estate) — findings register

Three runs against https://impilo.mohcc.gov.zw (estate `efedd0f49`, repo up to `b8576ef16`). The harness had
never truly run against the live shell before; each run peeled a real layer. Verified findings, most severe first:

| # | Finding | Class | Status |
| --- | --- | --- | --- |
| 1 | **Operators bounced off work routes to /home**: the session-experience queryKey includes facility/assignment state, so every context change flashed the contract to `undefined` and the citizen-only guard redirected (e.g. records clerk with ACTIVE assignment + work-tab-visible contract). | Product (shell) | **Fixed** (`b8576ef16`): contract kept across key swaps; guard never bounces while the contract loads |
| 2 | **VITO denies `/v1/client-registry/**` in the estate (403) → clerk cannot search the MPI** → clinical-day, diagnostics journeys blocked. Forensics: byte-identical jar (md5 match) with byte-identical env answers **200 locally** and **403 in-pod**, surviving a pod restart; the flag-gated preview chain provably works outside the estate. Estate-only runtime drift — certified fullboot is the corrective. | Estate drift | **Open — needs fullboot** |
| 3 | **UI masked the registry outage as "No patients found"** — clerk cannot distinguish an empty result from a down registry. | Product (honesty) | **Fixed**: walk-in search shows a registry-unreachable state on query error |
| 4 | Harness never completed the real work-session flow (facility → workspace → shift) and used pre-redesign selectors — all six specs failed at login/menu. | Harness | **Fixed**: ensureFacilityContext walks the real flow; dock/menu selectors realigned |
| 5 | Playwright could not reach the public hostname from the VM (hairpin NAT). | Harness/env | **Fixed**: runner auto-detects and maps the host to the local ingress for Chromium |
| 6 | Telehealth spec's Node-side API login cannot resolve the public host (Chromium resolver rules don't apply to APIRequestContext). | Harness/env | Open (known "browser-video" item) |
| 7 | Trust Console journey leg: queue tabs not visible for iatg.gono in the run — investigation queued behind finding #2 (shared estate-drift suspicion). | TBD | Open |

Progression: run 1 — all specs dead at the address bar; run 2 — all six reached login, four advanced past it;
run 3 — start-menu 5/6 checkpoints green, clerk reached walk-in registration with facility context and hit
finding #2. The harness is now a truthful instrument; the remaining blockers are estate-level.

## Recommendation

Run the **certified fullboot at final HEAD** (prepare already passes: CHART_INTEGRITY_PASS, PREFLIGHT_PASS,
dry-run clean). It is now both the certification step *and* the corrective for finding #2 (and likely #7),
after which the golden journeys should be re-run to closure.
