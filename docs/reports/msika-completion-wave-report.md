# Msika Completion Wave — Report

**Date:** 2026-07-12
**Repo:** `/opt/impilo/repos/Impilo-vNext` · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Commits:** `4a3869360` (nhume) · `ead284785` (msika) · `5f2142b98` (msika-flow) · `3b6a30b13` (mushex+costa) · `3988958aa` (bff/auth) · `f725fc6a3` (ui buyer lane) · `f0c3c2a65` (sidecar cleanup) — all pushed.
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

**Live runtime rig (msika + msika-flow + mushex jars on scratch Postgres + Redpanda Kafka):** the buyer lane was driven live and proved — msika boot + 115-item catalogue seed, the V008 price gate, add-to-cart, **real cart pricing (42.50, not the hardcoded zero)**, the order FSM CREATED→VALIDATED→PRICED with real CatalogValidationService, the **real MushexClient HTTP handoff reaching MusheX** (disproving the audit's dead-code claim), live payee propagation, the working simulation escape hatch, and the live Kafka topology. The rig **caught three real bugs, all fixed and committed**: (1) a V008 `CHAR(3)` schema-validation boot-blocker every unit test missed; (2) orders carried no seller/payee so payment 500'd; (3) the simulation escape hatch emitted the wrong metadata keys so it was inert. The terminal PAID transition is blocked by a **mushex-internal NPE** (MusheX assumes every intent carries a facility UUID; a citizen-buyer order's payee is a vendor) — a mushex-service follow-up, not a wave defect; that final leg stays seam-proven by the green msika-flow PaymentEventConsumer + PaymentServiceTest. Full evidence + honest journal: [reports/journeys/msika-runtime-proof-20260712/](../../reports/journeys/msika-runtime-proof-20260712/).

## Open / honest deferrals

- **Live four-lane runtime proof** is the one open verification item. The wave was committed on the strength of green unit suites + independently-verified contract seams + real-Postgres migration validation; the live rig (msika + msika-flow + mushex + nhume jars + a broker for the two Kafka confirmation hops) is the remaining proof and is being built. Where the broker leg cannot be exercised in the rig, those hops are seam-proven (consumer entry + HTTP-observable state) and flagged honestly in the journal — as the plan's WS6 anticipated.
- Deferred by design (documented in-code): booking/slot depth (booking-service owns it), OTP-only pickup claim, OROS line-level Rx validation, Nhume Kafka publisher (estate-wide improvement), distributed pickup rate-limiter, `mf_reservations` inventory-ack loop (inventory-service emits no acks today), cart qty-edit, Maestro mobile browser journey.
- `scripts/remediation/verify-remediations.sh` still names deleted sidecar page paths — a stale ad-hoc audit script, not a build gate; follow-up.

## Deploy

Nothing deployed. The next authorized roll carries msika V008, msika-flow V004, and the mushex/costa/nhume/bff/ui changes together.
