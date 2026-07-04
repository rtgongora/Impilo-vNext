# Scenario B — Billing + Coverage + Claim + Shortfall Card (Runbook)

Encounter-driven auto-billing → payer/patient split → claim filed on finalize →
scriptable adjudication → patient shortfall paid by card via the MusheX SANDBOX
rail → settled. Also proves Scenario D's failure path (no cover → 100% patient).
12 checks.

## Proof script

```bash
bash test/integration/scenario-b-billing-coverage-shortfall.sh
bash scripts/test/run-scenario-b-smoke.sh                # gate-wrapper form (post-deploy lane)
```

## Preconditions

- Scenario A preconditions (estate, seeds, personas).
- Coverage plans live in the `coverage` DB (`cv_coverage_plans`:
  `COV-MOHCC-CORE` 90/10, `COV-PRIVATE-PLUS` 80/20) — mirrored into costa by
  migration V019; AHFOZ-indicative tariffs by V020 (PO direction: Zimbabwe bills
  against AHFOZ; the real schedule arrives via costa's governed tariff import
  `POST /costa/v1/tariffs/import`, not preview seeds).
- Kafka opt-ins live for costing-engine (+`MUSHEX_BASE_URL`, `COVERAGE_BASE_URL`),
  mushex (+credential-verification URLs), coverage, pharmacy, inventory.

## Flow the script drives

1. Enrol patient as coverage member (plan id resolved via psql — no catalog API).
2. Real PCT encounter → costa consumes `pct.encounter.started` → **auto DRAFT bill**.
3. Charge lines priced from AHFOZ-indicative tariffs (CONSULT-GP 25.00, LAB-FBC 12.50).
4. `POST /costa/v1/bills/{id}/apply-coverage` → ELIGIBLE split (insurer 30.00 / patient 7.50 on 37.50).
5. Finalize → cv_claim filed (`coverageStatus=CLAIM_SUBMITTED:<uuid>`).
6. `POST /internal/v1/coverage/claims/{id}/adjudicate` (scriptable payer step).
7. Create payment intent `{"paymentType":"REMAINDER","amount":<patient>}` —
   costa calls MusheX synchronously and persists `mushex_payment_intent_id`.
8. MusheX attempt + `POST /mushex/v1/adapters/SANDBOX/webhook {adapterRef, status:"SUCCESS"}`.
9. Poll costa payment → **PAID** via `mushex.payment.status.changed`.
10. Negative path: un-enrolled patient → `INELIGIBLE:NO_COVER`, 100% patient.

## Contracts worth remembering

- The script self-provisions the facility payee credential
  (`FACILITY_OPERATING_LICENCE` via credential-verification `/v1/internal/credentials`) —
  MusheX refuses intents for uncredentialed payees.
- MusheX attempts body is `{idempotencyKey, reason}` (rail comes from the intent).
- Costa DB is named `costing_engine`; every coverage-service mutation needs an
  `Idempotency-Key`.

## UI surfacing

`/finance/billing/[id]` shows the coverage split (insurer/patient payable +
status badge), an Apply Coverage action, and prefills the payment form with the
patient shortfall (REMAINDER) when an insurer share exists.

## Known limits

- Adjudication is a scripted endpoint, not a payer integration — the real payer
  EDI/portal loop is out of scope for preview.
- Pre-service coverage enforcement is PARKED as decision
  `docs/decisions/DEC-0001-coverage-pre-service-enforcement.md` (recommendation:
  advisory banner + bill-time enforcement; emergency always exempt).
