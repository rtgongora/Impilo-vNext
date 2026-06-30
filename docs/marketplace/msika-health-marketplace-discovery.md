# Msika Health Marketplace — Discovery & Ownership Note

> Workstream #4 of the sequential feature sprint (branch `feature/msika-health-marketplace`).
> Read alongside `docs/audits/feature-sprint-ledger.md`. This note documents what already exists,
> the ownership-decision table, and the EXTEND-vs-BUILD plan **before** any code.

## 1. What Msika already has (mapped, not assumed)

Msika is **three real microservices**, not a greenfield:

### msika-service (port 8086, db `msika`) — Catalogue / Offering Registry (SoR)
- **Migrations V001–V005** (highest = **V005**). Tables: `msika_catalogs`, `msika_catalog_items`
  (6 kinds PRODUCT/SERVICE/ORDERABLE/CHARGEABLE/CAPABILITY_FACILITY/CAPABILITY_PROVIDER),
  `msika_product_details`, `msika_service_details`, `msika_orderable_details`,
  `msika_chargeable_details`, `msika_capability_links`, `msika_external_sources/mappings`,
  `msika_import_jobs`, `msika_change_log`, `msika_event_outbox`.
- **V004** added `risk_classification` (UNRESTRICTED→REGULATED) + `msika_risk_friction_mapping`
  (friction MINIMAL→MAXIMUM, requires_provider/facility, max_qty_per_order). **This is the
  regulation/clinical-safety gate substrate — reuse it, do not reinvent.**
- **V005** added `msika_offerings` (who offers what, where: provider/facility/vendor ref,
  availability_state, inventory_mode, booking_mode, pickup/delivery modes, geographic_coverage),
  `msika_fulfillment_policies`, `msika_governance_records` (CATALOG/ITEM/OFFERING/POLICY review
  with PENDING/APPROVED/REJECTED decisions).
- Controllers: Catalog, Item, Offering, FulfillmentPolicy, **Governance** (approve/reject/queue),
  Mapping, Import, Search (FTS), Pack, Validation, Snapshot.
- Patterns: `TrustContextHolder.require()`, `UlidGenerator`, transactional **outbox**
  (`OutboxPublisher extends CompanionOutboxPublisher`), `msika_change_log` audit, `JsonSupport`.
- **No downstream clients** — msika-service is a registry; it publishes events, others consume.

### msika-flow-service (port 8100, db `msikaflow`) — Transaction Orchestration (SoR)
- **Already owns the buyer transaction engine.** Tables `mf_orders`, `mf_order_lines`,
  `mf_order_events`, `mf_carts`/`mf_cart_items`, `mf_fulfillment_routes`/`mf_fulfillment_orders`,
  `mf_reservations` (inventory/pharmacy holds), `mf_pickup_tokens`, `mf_vendor_profiles`/documents,
  `mf_settlements` (**`mushex_payment_intent_id`**), `mf_refunds` (**`mushex_refund_id`**),
  `mf_logistics_*`, `mf_ops_reviews`.
- OrderController: `validate / price / pay / route / accept / mark-ready / mark-delivered /
  tracking / cancel`; CartsController checkout; BookingController; RefundController; RxController
  (substitution); VendorController; LogisticsController; OpsController.
- **CONCLUSION: the order lifecycle, MusheX settlement/refund handoff, reservation handoff,
  cancel, refund and tracking ALREADY EXIST here. We must NOT build a second order/transaction
  engine. The storefront lane composes msika-flow, it does not replace it.**

### msika-apps-service (port 8181) — Capability/App Marketplace (apps/plugins) — out of scope here.

### Experience layer that already exists
- **BFF**: `MsikaServiceClient` + `MsikaFlowServiceClient` + controllers MarketplaceController,
  MarketplaceOpsController, ProductRegistryController, VendorOperationsController,
  CommerceFlowController, CommerceSubstitutionController, HealthOsMarketplaceController.
  Endpoints under `/internal/v1/marketplace/*` (orders/catalog/vendors/bookings) — **facility/ops
  oriented**.
- **Web**: `/marketplace` hub + `catalog, orders, orders/[id], ops, vendor, vendor/orders,
  pickup, vendors, bookings, cart, substitutions, apps` (12 routes). **Facility/operator oriented**
  (procurement, vendor ops). EXPECTED_ROUTE_COUNT = **642**.
- **OPA**: `impilo.marketplace` exists but governs the **developer/API onboarding pipeline**
  (sandbox→production), NOT the buyer storefront. A new `impilo.msika` is therefore distinct.

## 2. The genuine gap (what this workstream adds)

A coherent **person/citizen-facing marketplace storefront lane**: discoverable **listings** over
offerings (seller-identity-validated, risk-classified, approved, published), a buyer **storefront
experience** (home / search / detail / activity) that **composes** the existing msika-flow
transaction engine + Costa billing + MusheX payment + inventory reservation + clinical handoff,
a **seller centre** (create/submit/publish/unpublish listings + view own activity), an
**approval/moderation** surface, and **Nompilo / Khuluma / Rito** hooks. None of this exists as a
person-facing lane today.

## 3. Ownership-decision table

| Capability | Existing owner found | Extend or Build | Notes |
|---|---|---|---|
| Catalogue items, risk classification, friction map | **msika-service** (V001/V004) | **Reuse** | Listings reference `catalog_item_id`; risk gate reuses `msika_risk_friction_mapping`. |
| Offering (who offers what/where) | **msika-service** `msika_offerings` (V005) | **Reuse** | A listing wraps an offering for buyer discovery. |
| Governance/approval records | **msika-service** `msika_governance_records` | **Reuse + extend** | Listing approval persisted as listing status + governance record. |
| **Buyer-discoverable listing / storefront** | none (offerings are admin-facing) | **BUILD** in msika-service | New `msika_listings`, `msika_storefronts`, `msika_listing_media`, `msika_listing_favourites`. |
| Order / cart / checkout / transaction lifecycle | **msika-flow-service** (`mf_orders` …) | **Reuse (compose)** | Storefront CTAs route to msika-flow order create/track. NO new order engine. |
| Billing / charge / bill | **Costa** (costing-engine, 8101) `/costa/v1/bills/*` | **Reuse (handoff)** | Display/request charge via BFF→Costa. Msika stores only a charge *ref*. |
| Payment / rails / receipt | **MusheX** (8102) `/mushex/v1/payment-intents/*` | **Reuse (handoff)** | Payment initiated by msika-flow `pay` (already wired to `mushex_payment_intent_id`). |
| Stock / availability / reservation | **inventory-service** (8098) `/v1/onhand`, `/v1/requisitions` | **Reuse (handoff)** | msika-flow already holds reservations (`mf_reservations`). Msika never decrements stock. |
| Clinical request / order | **PCT** (8088 referrals) / **OROS** (8089 intake) | **Reuse (handoff)** | Regulated/clinical listings route to PCT/OROS; Msika links the txn, never owns clinical truth. |
| Comms / notifications | **Khuluma** (8390) `/internal/v1/khuluma/delivery/dispatch` | **Reuse (request)** | Storefront update events request Khuluma delivery via BFF. |
| Guidance (Nompilo) | **guidance-service** (8260) `guidance.guidance_item` (V004/V005) | **Reuse + seed** | Add `domain='msika'` seed rows (V006); render `<NompiloContextualGuidance/>`. |
| Feedback / complaint / safety | **Rito** (8391) `/internal/v1/rito/cases` | **Reuse (link)** | "Report a listing/order" routes to Rito case create. |
| Provider identity / scope | **Varapi** | **Reuse (validate)** | Seller-identity validation seam on listing submit. |
| Facility / site status | **Tuso/Indawo** | **Reuse (validate)** | Facility-seller validation seam on listing submit. |

## 4. Build plan (this workstream)

1. **msika-service V006** — storefront tables (listings/media/storefronts/favourites/audit). Msika-owned only.
2. **Listing + Storefront + Favourite services & controllers** — lifecycle DRAFT→SUBMITTED→
   AWAITING_APPROVAL→APPROVED/REJECTED→PUBLISHED→UNPUBLISHED→SUSPENDED, seller-validation seam,
   risk gate, approval gate, outbox + audit on every sensitive/regulated/approval/cross action.
3. **experience-bff** — `MarketplaceStorefrontController` (buyer home/search/detail/favourite,
   compose Costa charge display + msika-flow tracking) + `SellerCentreController`; extend
   `MsikaServiceClient`. Safe-partial on each downstream.
4. **Web** — buyer storefront routes + seller centre + moderation; real hooks; Nompilo guidance.
5. **Mobile** — citizen-app `MarketplaceStorefrontSection` + service (written, not test-run).
6. **OPA** `impilo.msika` + tests (view/search/detail, buy self/dependant, regulated gate,
   create-listing provider/facility, publish, approve/reject/suspend, seller-centre, buyer/seller
   txn view, cancel/refund, billing/payment/fulfilment status view, Rito link, Khuluma request,
   low-trust restriction, programme-sponsored, aggregate-vs-user reporting).
7. **guidance-service V006** — `domain='msika'` seed rows.
8. Docs + one ledger row.

## 5. Deferred / honest seams
- **Costa bill display** is a real BFF→Costa read; **bill *creation*** stays a msika-flow/Costa
  concern triggered at checkout (not duplicated in the storefront).
- **PCT/OROS clinical handoff**: storefront detects a clinical/regulated listing and routes the
  buyer to the owning clinical intake; deep auto-creation of the clinical order is left to the
  clinical flow (documented, not faked).
- **Dura naming**: there is no `dura` service in this repo — **inventory-service (8098) is the
  stock SoR**; eLMIS adapter (8108) is read-only sync. Stock/reservation already lives in
  msika-flow `mf_reservations`. Coordination item raised for the registry to clarify the
  "Dura" alias → inventory-service mapping.
