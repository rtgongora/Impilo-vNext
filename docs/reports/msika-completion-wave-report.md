# Msika Completion Wave — Report

**Date:** 2026-07-12
**Repo:** `/opt/impilo/repos/Impilo-vNext` · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Commits:** `4a3869360` (nhume) · `ead284785` (msika) · `5f2142b98` (msika-flow) · `3b6a30b13` (mushex+costa) · `3988958aa` (bff/auth) · `f725f3a` (ui buyer lane) · `f0c3c2a65` (sidecar cleanup) — plus the M10 gap-closure commits (`02cd498b6`…) that took every lane to live-green. All pushed.
**Not deployed** (awaits explicit authorization).

## Why this wave

The PO's read — "Msika, Msika Flow are very thin" — was confirmed by three depth-audits: broad, cleanly-structured scaffolding whose **load-bearing beams were missing**. The money loop was fiction (payment intents fabricated locally; the MusheX client was orphaned dead code; no COSTA billing hook was ever called); no order could reach COMPLETED; home-delivery threw on an FSM guard; refunds never settled; cart checkout priced everything at $0 and **no add-to-cart action existed anywhere in the shell**; the catalogue was empty at boot; search silently dropped filters and fabricated a random tenant; risk-friction/offering/policy data was seeded but never enforced; the citizen-mobile controller was a semantic shim; no marketplace personas existed; four orphaned sidecar apps duplicated the shell.

Scope (PO): all four lanes end-to-end (buyer, seller/vendor, operator, citizen mobile); a **real money leg** (COSTA ↔ MusheX); fulfilment integrating **Nhume** (no parallel logistics).

## SoR boundaries honoured

msika = catalogue/listing truth incl. the new numeric listing price. msika-flow = order orchestration only (amounts are snapshots). COSTA = money truth. MusheX = payment execution. Nhume = dispatch truth. No duplicate systems of record introduced.

## What closed

**Money loop (msika-flow ↔ MusheX ↔ COSTA).** A key discovery de-risked it: COSTA *already* consumes `msika.flow.order.priced` and mints idempotent MARKETPLACE_ORDER charge records, and MusheX's seam existed but was built backwards (intent-on-paid, and `TrustContextHolder.require()` on a Kafka thread — unrunnable). The estate had already retired that pattern for COSTA in favour of a **synchronous HTTP intent handoff**. So: `MushexClient` rewritten as a real HTTP port of COSTA's `MushexPaymentIntentClient` (forwarded trust headers, idempotency key, fail-loud); `PaymentService` persists the real intent id; settlement splits derive from the price snapshot (the 90/10-vs-1.5% inconsistency is gone). The mushex backwards consumers are retired to log-only observers. Refunds execute over HTTP and settle via a new `MushexRefundEventConsumer` → `completeRefund` → order REFUNDED. COSTA gained settle/refund listeners (`msika.flow.order.paid` → charge SETTLED; `refund.completed` → REFUNDED); msika-flow persists the returned `costa_charge_ref`.

**Pricing + catalogue.** msika **V008**: numeric listing price columns + publish gate (sellable kinds require a price); a **115-item indicative starter catalogue** (EDLIZ-indicative medicines + equipment aligned to the 46 V007 sourcing categories, provenance INDICATIVE, no invented prices). msika-flow **V004** snapshot/ref columns. `CartService.checkout` now resolves each line's price server-side via `MsikaListingClient` (the hardcoded `BigDecimal.ZERO` is gone; unpriced ⇒ 422 per-line).

**Order lifecycle + real validation.** `OUT_FOR_DELIVERY` and `COMPLETED` are reachable (home-delivery no longer throws); `CatalogValidationService.validateCart` — the four real integration clients (MsikaCore/Tuso/Varapi/Vito) — is wired into both order-validate and checkout, and now enforces the risk-friction `max_qty_per_order` ceiling via the new msika endpoint. Bookings reach BOOKED/ATTENDED.

**Nhume fulfilment (HTTP both directions — Nhume has no Kafka publisher).** msika-flow's `NhumeClient` creates `MARKETPLACE` missions on delivery-mode orders; `InternalFulfillmentController` accepts Nhume's write-backs (`POST /internal/v1/msika-flow/orders/{id}/dispatch-status` — PICKED_UP→out-for-delivery, DELIVERED→delivered, with custody/handoff append). Nhume's `HttpNhumeWriteBackGateway.msikaFlowDispatchStatus` posts on pickup + delivery, fire-and-forget (courier sign-off never blocks). msika-flow's inert mission-lifecycle stubs are retired with a Javadoc pointing at Nhume as dispatch SoR.

**Vendor/ops + Rx.** reject now sets vendor REJECTED; suspend/reinstate wired; `GET /v1/vendors` list/detail/by-actor + `bind-actor`; `GET /v1/ops/audit`. A new msika consumer of `msika.flow.vendor.approved|suspended` reconciles the storefront (the first real estate consumer of those events). Rx: substitutions list/reject un-404'd; `attach-token` verifies against share-slip-service (honest 422/502, no more fake success).

**msika-service depth.** Search filters honored + the random-tenant bug removed; risk-friction enforced at publish + exposed for consumption; offering/policy resolution endpoint; import loop emits PENDING mappings + a worker that fails unsupported sources honestly; snapshot-emit writes a real outbox event; `validatePack` made real; `@PreAuthorize` gates on the previously-open mutation endpoints.

**Experience.** Add-to-cart (the structural gap) + real cart + a new `/marketplace/orders/[id]/pay` page composing the wallet hooks for the MusheX confirm → poll to PAID → success; lifecycle-aware order actions, extended timeline, Nhume delivery panel, pickup slip. Three realm roles (MARKETPLACE_OPERATOR/SELLER/VENDOR) with per-lane route gates (blanket COMMERCE untouched — regression-safe); msika.operator/seller/vendor personas seeded. Authenticated vendor binding replaces the free-text localStorage id. CitizenMarketplaceController rewritten honestly. Four orphaned sidecar apps deleted.

## Verification status

**Unit + build gates — all green (independently re-verified, not just agent-reported):**

| Service | Tests |
|---|---|
| msika-service | 78/78 (was 37) |
| msika-flow-service | 72/72 |
| mushex-service | 217/217 |
| costing-engine-service | 128/128 |
| nhume-service | 32/32 |
| experience-bff | 866/866 |
| one-ui-shell | tsc clean · 1611 vitest · audit-ui-integrity 0 findings · routes 702 |
| citizen-app | 13/13 marketplace |

**Cross-agent contract seams — independently verified aligned** (the four backend services were built in parallel to a relayed spec, so I checked producer vs consumer directly): listing `priceAmount`/`priceCurrency` (msika ↔ MsikaListingClient); risk-friction fields + the max-qty consumer wiring (I closed this gap myself — the endpoint existed but nothing consumed it); COSTA event topics `msika.flow.order.paid`/`refund.completed` (emitter ↔ listener); Nhume `CreateDeliveryRequest` `@JsonProperty` names ↔ NhumeClient; dispatch-status `{status,deliveryId,proofRef}` both ends. V008 was applied V001→V008 on a real Postgres and validated (115 items, idempotent, no dangling category refs).

**Live runtime rig — ALL FOUR LANES GREEN (M10 closure, 2026-07-12).** The reusable rig `scripts/runtime-proof/msika-journeys.sh` boots msika, msika-flow (nhume-enabled), mushex (sandbox), nhume, costa and experience-bff from jars on scratch Postgres/Redpanda/Redis and drove **25/25 checks green** ([evidence](../../reports/journeys/msika-runtime-proof-20260712-full/)):

- **J1 money loop end-to-end**: real cart pricing (42.50) → payee = listing seller → **real MusheX intent** → sandbox settle → Kafka `payment.status.changed` → order **PAID** → **Costa charge SETTLED** → `costa_charge_ref` written back → refund → `mushex.refund.status.changed` → order **REFUNDED** → **Costa charge REFUNDED**.
- **J1b delivery leg**: HOME_DELIVERY order → **real Nhume MARKETPLACE mission** (verified in `impilo_nhume` with `marketplace_order_ref`) → PICKED_UP/DELIVERED dispatch-status write-backs → OUT_FOR_DELIVERY → DELIVERED → **COMPLETED**. (The courier write-back POST was driven by the rig to Nhume's exact contract; Nhume's own gateway remains covered by its 32 unit tests.)
- **J2 seller/vendor**: apply → ops approve → `msika.flow.vendor.approved` → **storefront auto-provisioned in msika (Kafka consumer live-proven)** → bind-actor → by-actor.
- **J3 operator**: suspend → vendor SUSPENDED + **storefront suspension cascade (Kafka)** → reinstate → ops audit feed.
- **J4 citizen mobile through the booted BFF**: services discovery → request create → truthful cancel.

**The rig caught seven more real bugs beyond the first run's three, all fixed + committed**: mushex NPE on facility-less vendor-payee intents; the sandbox settle flipping intents PAID *silently* (no STATUS_CHANGED event — the whole simulated money loop was unobservable); no refund could ever complete estate-wide (no adapter callback surface — sandbox parity added) and refund events carried no status on the wire (consumers ignored them); the msika-flow→Nhume hop was dead three ways (missing v1.1 hard-required headers — the MISSING_REQUIRED_HEADER defect family again; the `/api/v1` path outside the V11 filter so RequestContext never set; response parsed as a `{data}` wrapper it isn't); citizen mobile requests 500'd because the BFF round-trips order `metadata` that `mf_orders` never had (V005 added) and 400'd on `SERVICE_BOOKING` vs the real `SERVICE_BOOKING_ORDER` enum; and courier write-backs stranded orders at ROUTED behind the FSM guard (dispatch signals now fast-forward lagging vendor states as audited transitions).

**Golden browser journeys**: `msika-buyer` / `msika-seller` / `msika-operator` specs registered in `run-golden-journeys.sh` (6 tests, RUN_PREVIEW-gated, honest-degraded alternates; not yet run against a live preview — part of the next deploy's journey pass).

## Open / honest deferrals

- **Pay-confirm production seam**: the shell's `useCommercePaymentConfirm` debits the Mushe wallet (reference = intent id) but nothing records that debit against the MusheX intent (`POST /{id}/attempts` has no BFF passthrough). Preview is fully covered by the sandbox auto-settle; wiring wallet-debit→attempt is the one open production-money seam. Documented in the hook.
- **Persona users are dev-realm only** — preview persona users are provisioned by the seed orchestrator at deploy time; verify at the next authorized roll (G10).
- Deferred by design (unchanged): booking/slot depth (booking-service owns it), OTP-only pickup claim, OROS line-level Rx validation, Nhume Kafka publisher (estate-wide improvement), distributed pickup rate-limiter, `mf_reservations` inventory-ack loop, cart qty-edit, Maestro mobile browser journey.
- `scripts/remediation/verify-remediations.sh` re-pointed at the shell pages that carry the retired sidecars' remediations — green.

## Deploy

Nothing deployed. The next authorized roll carries msika V008, msika-flow V004+V005, and the mushex/costa/nhume/bff/ui changes together.
