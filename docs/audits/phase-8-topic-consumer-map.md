# Phase 8F — MusheX/COSTA topic consumer map

| Field | Value |
| ----- | ----- |
| Status | Implemented (Phase 8F) |
| Scope | In-repository consumers of MusheX/COSTA finance outbox topics |
| Source | `@KafkaListener` call-sites and architecture docs in this repository |

## 1. MusheX topics → known consumers

| Topic | Known in-repo consumers |
| ----- | ----------------------- |
| `mushex.payment.status.changed` | `costing-engine-service` (`CostaEventConsumer`), `experience-bff` (`UpstreamEventConsumer`), `mushe-wallet-service` (`WalletEventConsumer`), `share-slip-service` (`ShareSlipEventConsumer`), `coverage-service` (`CoverageEventConsumer`), `varapi-service` (`MushexPaymentStatusChangedListener`) |
| `mushex.refund.status.changed` | `costing-engine-service` (`CostaEventConsumer`), `experience-bff` (`UpstreamEventConsumer`) |
| `mushex.claim.adjudicated` | `coverage-service` (`CoverageEventConsumer`), `share-slip-service` (`ShareSlipEventConsumer`) |
| `mushex.claim.submitted` | No direct `@KafkaListener` found in this repository snapshot |
| `mushex.settlement.batch.released` | `coverage-service` (`CoverageEventConsumer`), `mushe-wallet-service` (`WalletEventConsumer`) |
| `mushex.remittance.issued` | `share-slip-service` (`ShareSlipEventConsumer`) |
| `mushex.payment.intent.created` | No direct `@KafkaListener` found in this repository snapshot |
| `mushex.payment.attempt.*` | No direct `@KafkaListener` found in this repository snapshot |
| `mushex.events` | No explicit direct consumer found (default/catch-all remains intentionally sparse) |

## 2. COSTA topics → known consumers

| Topic | Known in-repo consumers |
| ----- | ----------------------- |
| `costa.bill.finalized` | `mushex-service` (`CostaEventConsumer`), `experience-bff` (`UpstreamEventConsumer`), `general-ledger-service` (`GlKafkaIntegrationListener`) |
| `costa.invoice.issued` | `mushex-service` (`CostaEventConsumer`) |
| `costa.refund.issued` | `mushex-service` (`CostaEventConsumer`) |
| `costa.payment.status_changed` | No direct consumer found in this repository snapshot |
| `costa.events` | No explicit direct consumer found (default/catch-all) |

## 3. Notes

- This map is intentionally repository-scoped; external consumers (outside this repo) are not included.
- “No direct consumer found” means no `@KafkaListener` topic reference was detected during this audit pass; it does not imply the topic is unused globally.
