# Runbook — Legacy VITO wallet drain (before dropping the deprecated ledger)

**Status:** ops procedure, run once, under sign-off. **NOT** a Flyway migration.
**Owner:** money-platform + VITO service owners. **Blast radius:** citizen money balances.

## Why this exists

`mushe-wallet-service` is the money **system-of-record**. VITO's `vito.wallet` /
`vito.wallet_journal` are a **deprecated parallel ledger** (`WalletService` /
`WalletController` are `@Deprecated`; see
[`docs/architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md`](../architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md) §9 and
[`docs/registry/system-of-record-map.md`](../registry/system-of-record-map.md)).

Any residual balance in `vito.wallet` is **real money**. Dropping those tables before
reconciling would lose it. This runbook drains every residual balance into the person's
mushe wallet, idempotently, then zeroes the VITO row — and only after the grand total
reaches zero may a `DROP` be scheduled.

> **This must not be a silent Flyway step.** A schema migration that moved balances would
> run unattended, with no dry-run, no per-row verification, and no idempotency against a
> partial previous run. Money movement is done here as an audited, resumable procedure.

## The one correctness risk — `health_id` ≠ CPID

VITO keys a wallet by **`health_id` (UUID)**; mushe finds the individual wallet by
**`owner_ref = <CPID>` (String), `owner_type = 'INDIVIDUAL'`**. These are **not the same
value**, and `vito.client` has no CPID column. So the drain **must** resolve
`health_id → CPID` for each wallet before crediting mushe. Do not assume they are equal.

Resolve via the registry/identity layer (the same CPID VITO already emits on its
`vito.identity` `CLIENT_CREATED` events — that is what mushe stored as `owner_ref` when it
auto-created the wallet). Confirm the mapping source with the identity owners before step 3.

## Detection (read-only, safe anytime)

Run [`legacy-vito-wallet-drain.sql`](legacy-vito-wallet-drain.sql). It:

1. Lists wallets with `balance > 0` (the work-list).
2. Totals residual per tenant/currency (the control figures).
3. Gives the **grand total** — the number that must reach `0` before any `DROP`.
4. Surfaces `unreconciled_entries` per wallet — **skip any wallet with unreconciled offline
   journal rows** until they settle (its balance may still change).
5. Provides the `health_id → CPID` gap-check template.

If the grand total is already `0`, there is nothing to drain — proceed straight to the
DROP gate below.

## Procedure (per residual wallet)

For each wallet from the work-list, in a controlled batch with sign-off:

1. **Skip if unsettled.** If `unreconciled_entries > 0`, defer this wallet.
2. **Resolve the person.** `cpid = resolve(health_id)`. If resolution fails, **stop and
   escalate** — never guess. Do not credit a wallet you cannot attribute.
3. **Find/confirm the mushe wallet.** `owner_type='INDIVIDUAL'`, `owner_ref=cpid`,
   matching `tenant_id` and `currency`. (Every registered patient already has an
   auto-created INDIVIDUAL wallet; if absent, create it through mushe, not by raw insert.)
4. **Credit mushe idempotently.** Credit `balance` into the mushe wallet through the
   **mushe internal wallet credit API** (so it goes through the double-entry ledger, limits,
   and outbox — not a raw SQL update), with a **stable idempotency key**:
   `vito-drain:<vito_wallet_id>`. Re-running the batch re-uses the same key, so a wallet is
   never double-credited. Use txn type e.g. `LEGACY_MIGRATION`, channel `VITO_DRAIN`,
   reference `<vito_wallet_id>`.
5. **Verify the credit landed** (the mushe transaction exists for that idempotency key).
6. **Zero the VITO wallet** — only after step 5 confirms — by writing a balancing
   `DEBIT` journal entry (`transaction_ref = vito-drain:<vito_wallet_id>`, respecting the
   `uq_journal_entry` unique constraint) and setting `vito.wallet.status = 'CLOSED'`,
   `balance = 0`. Do this through VITO's own service path if one is added, or as an audited
   SQL statement inside the same maintenance window. Never zero VITO before the mushe credit
   is confirmed.

Ordering rule: **credit mushe → verify → zero VITO.** If the process dies between steps,
re-running is safe: the mushe credit is idempotent, and a wallet already zeroed drops out of
the `balance > 0` work-list.

## Reconciliation gate

After the batch:

- Re-run detection query **3**. Grand total residual must be `0` (for settled wallets).
- The **sum credited into mushe** under `channel='VITO_DRAIN'` must equal the **original
  grand total** from the pre-drain run (record it before you start).
- Any wallet still `> 0` is either unsettled (expected — revisit) or failed resolution
  (escalate). Do not proceed to DROP while any settled wallet remains non-zero.

## DROP gate (separate, later, sign-off required)

Only when detection query 3 reports `grand_total_residual = 0` **and**
`wallets_to_drain = 0` for all settled wallets, a **subsequent** VITO Flyway migration may
drop `vito.wallet_journal` then `vito.wallet` (journal first — it FKs the wallet). That drop
migration is out of scope here and must reference this runbook and the reconciliation
evidence in its description. Keep a dated export of `vito.wallet` + `vito.wallet_journal`
(and the mushe drain transactions) before the drop.

## Rollback

Before the drop, rollback is inherent: the mushe credits are real ledger entries (reverse
with a compensating debit if a batch was run in error, keyed off the same
`vito-drain:<id>`), and VITO rows are only zeroed after their mushe credit is confirmed.
After the drop there is no rollback — hence the export requirement above.
