# COSTA and MusheX financial foundation (wave 1)

This note describes the first production-oriented slice merged into the costing engine (COSTA) and MusheX services: correct MusheX payment status consumption, invoice enrichment, payment allocations, wallet and remittance-transfer APIs, and related persistence.

## COSTA

- **Flyway `V005__financial_lifecycle.sql`** adds costing profile, cost record, charge record, billing account, statement, payment allocation, invoice line, and financial audit tables; extends `costa_invoices` with tenant, lifecycle amounts, due date, and metadata.
- **`CostaEventConsumer`** reads MusheX Kafka payloads using `intentId`, `toStatus`, and `amountPaid` (with legacy fallbacks `paymentIntentId`, `status`, `paidAmount`). Idempotency uses `eventId` when present, otherwise `intentId:toStatus`.
- **`PaymentIntegrationService`** maps MusheX intent statuses into `PaymentStatus` (`PAID`, `FAILED`, `CANCELLED`, `PENDING` for `CREATED`/`PENDING`/`AUTHORIZED`). Receivables and patient-account capture run only on the first transition into `PAID` for a payment.
- **`PaymentAllocationService`** writes `costa_payment_allocations`, updates invoice `settled_amount` / `outstanding_amount` / `invoice_status` (`PARTIALLY_PAID` / `PAID`), and emits outbox `PAYMENT_ALLOCATED`.
- **`issueInvoice`** creates `costa_invoice_lines` from non-void bill lines and sets invoice totals from the bill.
- **REST** `FinancialLifecycleController` under `/costa/v1/finance/lifecycle` exposes invoice detail, lines, and allocations (tenant-scoped).

## MusheX

- **Flyway `V004__financial_platform.sql`** adds wallet accounts and transactions, remittance requests (domestic/international fields), card profiles, reversal records, and financial audit log tables.
- **`WalletPlatformService` / `WalletPlatformController`** (`/mushex/v1/platform/wallets`) support create, list by owner, credit, debit, and transaction history with outbox `WALLET_CREATED` and `WALLET_TRANSACTION_RECORDED`.
- **`RemittanceTransferService` / `RemittanceTransferController`** (`/mushex/v1/remittance-transfers`) support listing and creating remittance requests with outbox `REMITTANCE_REQUESTED`.
- **`OutboxPublisher.routeTopic`** maps the new event types to `mushex.wallet.created`, `mushex.wallet.transaction.recorded`, and `mushex.remittance.requested`.
- **Compile fix:** Hibernate `@JdbcTypeCode` / `SqlTypes` imports were added to several existing MusheX entities that referenced the annotations without imports.

## Integration

- MusheX remains the source of payment intent status; COSTA aligns via `mushex_payment_intent_id` on `costa_payments` and the `mushex.payment.status.changed` topic.
- Allocations link COSTA payments and invoices to MusheX intent ids for audit and downstream workflows.

## Wave 2 (billing workspace, refunds, Msika Flow, cards)

- **COSTA `V006__refund_invoice_reversal_flag.sql`:** `costa_refunds.allocation_reversed` prevents double application of invoice unwind when MusheX emits duplicate refund events.
- **`PaymentAllocationService.reverseInvoiceForRefund`:** LIFO unwinds `SETTLED` rows on `costa_payment_allocations`, marks them `REVERSED` (or shrinks partial rows), recomputes invoice settled/outstanding, and emits outbox `INVOICE_REFUND_APPLIED` → Kafka `costa.invoice.refund_applied`.
- **`CostaEventConsumer`:** on `mushex.refund.status.changed` with `COMPLETED`, runs the reversal once per COSTA refund; listens on `msika.flow.order.priced` and idempotently creates `costa_charge_records` via `ChargeRecordService` (outbox `CHARGE_CREATED` → `costa.charge.created`).
- **`FinancialLifecycleController`:** `POST /costa/v1/finance/lifecycle/charges`, `GET .../charges?billId=`.
- **Msika Flow:** `OrderStateMachine` outbox payload includes `patientCpid` and `facilityId` when present; `PaymentEventConsumer` accepts MusheX `intentId` / `toStatus` (and envelope `payload`); `PaymentService` ignores duplicate `PAID` / `FAILED` callbacks.
- **MusheX:** JPA for `mushex_card_profiles` and `mushex_reversal_records` with `/mushex/v1/platform/card-profiles` and `/mushex/v1/platform/reversals`.
- **Tshepo `V007__finance_workspace_policy_rules.sql`:** synthetic resource types `billing-workspace` and `mushex-platform` for BFF paths `/internal/v1/finance/billing-workspace` and `/internal/v1/finance/mushex-platform`.
- **Experience BFF:** `FinanceBillingWorkspaceController`, `FinanceMushexPlatformController`, `FinancePlaneAuthorizationService`, `impilo.finance.*` YAML flags; UI route `/finance/workspace`.

## Known gaps (later)

- Card issuance flows and external PSP adapters; richer reversal completion workflows.
- Automated integration tests across Kafka topics for refund + priced order paths.

## Manual checks

1. Run Flyway for `costing-engine-service` and `mushex-service` databases.
2. Finalize a bill, issue an invoice, create a COSTA payment with `mushex_payment_intent_id`, publish `mushex.payment.status.changed` with `{"intentId":"...","toStatus":"PAID","amountPaid":"10.00"}` and confirm receivables, allocations, and invoice balances.
3. `POST /mushex/v1/platform/wallets` then `POST .../credit` and `GET .../transactions` with trust headers.
4. `POST /mushex/v1/remittance-transfers` and `GET` with `senderRef`.
5. Publish `msika.flow.order.priced` with `orderId`, `tenantId`, `amountTotal`, optional `patientCpid` / `facilityId`; confirm a `costa_charge_records` row appears once.
6. After a paid invoice exists, complete a MusheX refund for the bill; confirm allocations reverse and `allocation_reversed` is true on the COSTA refund row.
7. `GET /internal/v1/finance/billing-workspace/lifecycle/invoices/{id}` via BFF (trust headers); `GET /internal/v1/finance/mushex-platform/wallets?ownerRef=` for custodial wallets.
