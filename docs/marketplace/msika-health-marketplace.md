# Msika Health Marketplace — Doctrine, Boundaries, Journeys & Parity

Workstream #4 of the feature sprint. Companion to the
[discovery note](msika-health-marketplace-discovery.md). This documents the marketplace
doctrine, ownership boundaries, the journeys delivered, the new API/BFF/policy surface, and
web/mobile parity.

## 1. Doctrine

Msika is the **health marketplace and transaction-discovery lane**: discover, request, book,
order, pay for, track, fulfil and review health/wellness services and products — while respecting
regulation, clinical safety, identity, trust, consent, stock truth, billing truth and payment
truth. Msika is the **discovery + listing + transaction-intent** lane. It is **not** a payment,
billing, inventory or clinical-care engine; it **coordinates with the correct owners**.

## 2. Ownership boundaries (what Msika owns vs references)

| Concern | Owner | Msika's role |
|---|---|---|
| Catalogue / offering / risk class | **msika-service** (V001/V004/V005) | owns |
| **Buyer-discoverable listings + storefronts** | **msika-service** (V006, this WS) | owns |
| Orders / cart / checkout / fulfilment / pickup / tracking | **msika-flow-service** (`mf_*`) | composes (no second order engine) |
| Billing / charges / bills | **Costa** (8101) | references charge ref; displays via BFF |
| Payments / rails / receipts | **MusheX** (8102) | references; payment initiated by msika-flow |
| Stock / availability / reservation | **inventory-service** (8098) (+ msika-flow holds) | references; never decrements stock |
| Clinical request / order | **PCT** (8088) / **OROS** (8089) | routes regulated/clinical listings; links txn |
| Comms / delivery | **Khuluma** (8390) | requests dispatch; never sends |
| Guidance (Nompilo) | **guidance-service** (8260) | seeds `domain='msika'`; renders the whisper panel |
| Feedback / safety | **Rito** (8391) | links a case; never owns it |
| Provider / facility / site identity | **Varapi / Tuso / Indawo** | validates seller identity (verification outcome only) |

> **"Dura" = inventory.** There is no `dura` service in this repo; **inventory-service (8098)** is
> the stock SoR and msika-flow already holds reservations (`mf_reservations`). See coordination items.

## 3. Listing lifecycle

`DRAFT → SUBMITTED → AWAITING_APPROVAL → APPROVED / REJECTED → PUBLISHED → UNPUBLISHED / SUSPENDED`

- **Seller-identity validation**: a listing's seller (provider/facility/site/programme/vendor) is
  validated against Varapi/Tuso/Indawo; the verification outcome is recorded on the storefront and
  audited (`SELLER_VALIDATE`).
- **Risk gate**: `HIGH_RISK` and `REGULATED` listings require a **VERIFIED** seller storefront to
  publish (defence in depth — the OPA policy also gates this; denial is audited `POLICY_DENIED`).
- **Regulated buyer view**: viewing a regulated published listing writes a `REGULATED_VIEW` audit row.
- Every create/submit/approve/reject/suspend/publish emits an outbox event **and** a
  `msika_listing_audit` row.

## 4. Journeys delivered

- **Buyer discovery → detail → intent**: `/marketplace/store` (featured) → `/marketplace/store/search`
  → `/marketplace/store/listing/[id]` with real CTAs (Book/Order → msika-flow cart; Request via care →
  PCT/OROS for clinical; Save → favourite; Report → Rito). Regulated/clinical gate shown before checkout.
- **Buyer tracking**: `/marketplace/store/activity` composes msika-flow orders + tracking (Costa
  billing + MusheX payment status surfaced via the order). Safe-partial when an owner is down.
- **Seller centre**: `/marketplace/seller` → create storefront, `/marketplace/seller/listings` (status),
  `/marketplace/seller/listings/new` (author), `/marketplace/seller/moderation` (operator approve/reject/suspend).
- **Guidance**: `<NompiloContextualGuidance/>` on every storefront/seller page, fed by guidance-service
  `domain='msika'` seeds (V006).
- **Updates**: `POST /internal/v1/marketplace/store/notify` requests a Khuluma dispatch.
- **Feedback/safety**: `POST /internal/v1/marketplace/store/feedback` creates a Rito case (source `MSIKA_MARKETPLACE`).

## 5. New surface

### msika-service (sovereign)
- **V006** tables: `msika_storefronts`, `msika_listings`, `msika_listing_media`,
  `msika_listing_favourites`, `msika_listing_audit`.
- `ListingController` (`/v1/listings/**`): search, detail, favourites, create/update/submit/media,
  seller listings, moderation queue, approve/reject/suspend, publish/unpublish.
- `StorefrontController` (`/v1/storefronts/**`): create, verify (validation outcome), get/list.
- Services: `ListingService`, `StorefrontService`, `FavouriteService`, `ListingAuditService`.

### experience-bff (composition)
- `MarketplaceStorefrontController` (`/internal/v1/marketplace/store/**`): home, search, listing,
  favourite, activity (+ `/{orderId}` timeline), feedback→Rito, notify→Khuluma.
- `SellerCentreController` (`/internal/v1/marketplace/seller/**`): storefront, listing authoring,
  moderation queue + approve/reject/suspend.
- `MsikaServiceClient` extended; `KhulumaServiceClient` added.

### policy (OPA)
- `impilo.msika` rego + tests (44 new; suite 118→162). Gates discovery, buy self/dependant,
  regulated eligibility, seller create provider/facility, publish, approve/reject/suspend, seller
  centre, buyer/seller txn views, cancel/refund, billing/payment/fulfilment status, Rito link,
  Khuluma request, low-trust restriction, programme mgmt, aggregate vs user-level reporting.
  Forbids owner-domain actions (stock/payment/billing/clinical).

### guidance-service
- **V006** seeds: 8 `domain='msika'` route-bound guidance items.

## 6. Web ↔ Mobile parity

| Surface | Web | Mobile (citizen-app) |
|---|---|---|
| Storefront home (featured) | `/marketplace/store` | `MarketplaceStoreSection` |
| Search | `/marketplace/store/search` | inline search in section |
| Listing detail + CTAs | `/marketplace/store/listing/[id]` | listing cards + regulated hint |
| Activity / tracking | `/marketplace/store/activity` | `→ bookings` (msika-flow) |
| Nompilo guidance | `NompiloContextualGuidance` | `NompiloGuidanceSection` |
| Seller centre | `/marketplace/seller/**` | (web-first; mobile is buyer-focused) |

Web is tsc-clean (modulo the one pre-existing serviceBranding error) and tested (routes 31/31,
store page 2/2). **Mobile is written and structurally verified against the real service/store
exports but NOT test-run** here (apps/mobile uses pnpm `workspace:*`; `npm install` fails with
EUNSUPPORTEDPROTOCOL — per the environment note). Honest gap, not a claim of passing mobile tests.

## 7. Deferred items / honest seams

- **Costa bill display vs creation**: the storefront displays a charge ref; bill *creation* stays a
  msika-flow/Costa concern triggered at checkout — not duplicated here.
- **MusheX**: payment is initiated by msika-flow's `pay` (already wired to `mushex_payment_intent_id`);
  the storefront surfaces status, it does not initiate payment itself.
- **Dura/inventory reservation**: msika-flow already holds reservations; the storefront does not
  reserve or decrement stock. Deep availability composition is left to msika-flow.
- **PCT/OROS clinical handoff**: regulated/clinical listings route the buyer to the clinical owner's
  intake; automatic clinical-order creation from a listing is left to the clinical flow (documented).
- **Seller verification upstream call**: `StorefrontService.verify` records the validation *outcome*;
  the live Varapi/Tuso/Indawo lookup is the operator's pre-step (seam documented).
