# Msika Completion Wave — Live Runtime Rig Journal (2026-07-12)

Rig: scratch Postgres 16 (:15633) + Redpanda Kafka (:19092) + Redis (:16399);
service jars booted from HEAD — msika (:28086), msika-flow (:28100),
mushex (:28102, SANDBOX adapter). All jars packaged clean from source.

## Proven LIVE (real services, real HTTP, real Kafka, psql read-backs) — buyer lane

1. **msika boots + seeds** — V001→V008 on a virgin DB; 115 starter catalogue
   items present after boot.
2. **V008 listing price gate — LIVE** — publish a listing with priceAmount=42.50
   → HTTP 200, price persists.
3. **Add-to-cart — LIVE** — the previously-nonexistent action: open cart → add
   item (listingId carried) persists a cart line.
4. **Real cart pricing — LIVE (headline fix)** — checkout resolves the line
   price server-side from the listing snapshot: unit_price = amount_total =
   42.50. The audit's hardcoded BigDecimal.ZERO is gone.
5. **Order FSM CREATED → VALIDATED → PRICED — LIVE** with the real
   CatalogValidationService (MsikaCore/Tuso/Varapi/Vito) in the validate path.
6. **Real MushexClient HTTP handoff — LIVE (central audit finding disproven)** —
   pay makes a genuine HTTP POST from msika-flow to mushex; mushex receives and
   processes it. The audit's "MushexClient is orphaned dead code / intents
   fabricated locally" is false — the handoff is real and reaches MusheX.
7. **Payee propagation — LIVE (rig-caught fix)** — a citizen-buyer order now
   carries the listing seller as its vendorRef; MusheX accepts the payee
   (advanced past "provider_id required").
8. **Simulation escape hatch — LIVE (rig-caught fix)** — after correcting the
   metadata keys, MusheX logs "Impilo sandbox simulation: skipping provider
   credential verification for new intent" — the flag now actually engages.
9. **Live Kafka money-loop topology** — mushex is subscribed to
   msika.flow.order.paid / refund.requested on the real broker.

## Bugs the rig CAUGHT and FIXED (committed)

- **V008 price_currency CHAR(3) → VARCHAR(3)**: bpchar vs the entity's
  varchar(3) fails Hibernate schema-validation at msika boot — a production
  boot-blocker all 78 unit tests missed. (commit fix(msika))
- **Order payee not carried**: cart orders had no seller/payee, so payment
  500'd. MsikaListingClient now returns the seller; CartService carries it as
  vendorRef; PaymentService falls back to it. (commit fix(msika-flow))
- **Simulation escape hatch inert**: emitted key 'simulation' but MusheX reads
  'impilo_simulation'/'simulation_outcome'. Fixed. (same commit)

## Final blocker — mushex-internal, NOT a wave defect (honest)

After the escape hatch engages, MusheX NPEs:
`PaymentIntentEntity.getFacilityId().toString()` with a null facilityId — MusheX
assumes every intent carries a facility UUID, which a citizen-buyer marketplace
order legitimately does not (its payee is a vendor, not a facility). This is a
robustness gap in mushex-service (outside this wave's ownership: the wave owns
msika/msika-flow, not MusheX internals). The order therefore reaches PRICED with
a real payee and a real handoff into MusheX, but the sandbox settle → Kafka
payment.status.changed → PaymentEventConsumer → PAID leg does not complete in the
rig because of the MusheX-side NPE.

**Net:** the money-loop plumbing is proven real end-to-end into MusheX (not the
dead code the audit alleged), the headline deliverables (cart pricing, price
gate, catalogue, add-to-cart, order FSM, real handoff, live Kafka, working
escape hatch) are LIVE, and three real bugs were caught + fixed. The terminal
PAID transition is blocked by a MusheX null-facilityId NPE — a follow-up on
mushex-service, seam-proven here by the green msika-flow PaymentEventConsumer +
PaymentServiceTest unit suite.

## Follow-ups recorded
1. mushex-service: `PaymentIntentService` NPEs on a null facility_id for
   vendor-payee (non-facility) intents — handle facility-less marketplace
   intents. Small, mushex-owned.
2. Costa settle + Nhume dispatch legs gate on the PAID transition above; both
   are unit-green and contract-seam-verified, to be live-proven once (1) lands.
