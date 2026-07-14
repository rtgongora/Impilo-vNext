-- ============================================================================
-- Legacy VITO wallet — residual-balance DETECTION (READ-ONLY)
-- ============================================================================
-- Unified SMART card closeout: mushe-wallet-service is the money system-of-record;
-- VITO's vito.wallet / vito.wallet_journal are a DEPRECATED parallel ledger. Before
-- those tables are ever dropped, any residual balance MUST be reconciled into the
-- person's mushe wallet (see docs/runbooks/legacy-vito-wallet-drain.md).
--
-- Every statement here is READ-ONLY (SELECT). It moves no money and is safe to run
-- against production at any time. The reconciliation itself is a governed, idempotent
-- procedure described in the runbook — deliberately NOT a silent Flyway migration.
-- ============================================================================

-- 1. Residual balances that must be drained (the work-list). The CHECK constraint
--    chk_balance_non_negative forbids negatives, so balance > 0 is the whole set.
SELECT w.id            AS vito_wallet_id,
       w.tenant_id,
       w.health_id,                       -- VITO person key (UUID) — NOT the mushe CPID
       w.card_id,
       w.currency,
       w.balance,
       w.status
FROM   vito.wallet w
WHERE  w.balance > 0
ORDER  BY w.tenant_id, w.balance DESC;

-- 2. Totals per tenant/currency — the control figure. After a full drain this must
--    match the sum credited into mushe (see runbook step 6, reconciliation gate).
SELECT w.tenant_id,
       w.currency,
       count(*)      AS wallets_with_balance,
       sum(w.balance) AS total_residual
FROM   vito.wallet w
WHERE  w.balance > 0
GROUP  BY w.tenant_id, w.currency
ORDER  BY w.tenant_id, w.currency;

-- 3. Grand total — the single number that must reach zero before any DROP is allowed.
SELECT coalesce(sum(w.balance), 0) AS grand_total_residual,
       count(*) FILTER (WHERE w.balance > 0) AS wallets_to_drain
FROM   vito.wallet w;

-- 4. The last reconciled journal entry id per wallet — used to build a STABLE
--    idempotency key for the mushe credit (idempotency key = 'vito-drain:' || vito_wallet_id).
--    Also surfaces any UNRECONCILED offline entries (reconciled = false): these represent
--    offline debits not yet applied, so the wallet's balance may still change — do NOT drain
--    a wallet that has unreconciled journal rows until they settle.
SELECT w.id AS vito_wallet_id,
       w.balance,
       max(j.id) AS last_journal_entry_id,
       count(*) FILTER (WHERE j.reconciled = false) AS unreconciled_entries
FROM   vito.wallet w
LEFT   JOIN vito.wallet_journal j ON j.wallet_id = w.id
WHERE  w.balance > 0
GROUP  BY w.id, w.balance
ORDER  BY unreconciled_entries DESC, w.balance DESC;

-- 5. CORRECTNESS PRE-FLIGHT — health_id → CPID resolution.
--    VITO keys the wallet by health_id (UUID); mushe finds the individual wallet by
--    owner_ref = CPID (a String), owner_type = 'INDIVIDUAL'. These are NOT the same value
--    and vito.client has no CPID column, so the drain MUST resolve health_id → CPID first
--    (via the registry/identity layer — see runbook step 2). If mushe and VITO share one
--    Postgres instance you can spot the join gap directly; if they are separate databases,
--    run the mushe half separately. Adjust owner_ref join once the CPID source is confirmed.
--
--    Example gap check (ONLY valid if you have a confirmed health_id→CPID mapping table/view
--    called resolve_cpid(health_id); otherwise treat resolution as a runbook step):
-- SELECT w.id AS vito_wallet_id, w.health_id, w.balance
-- FROM   vito.wallet w
-- WHERE  w.balance > 0
--   AND  NOT EXISTS (
--         SELECT 1 FROM mushe.wallets m
--         WHERE m.owner_type = 'INDIVIDUAL'
--           AND m.owner_ref = resolve_cpid(w.health_id)   -- <-- confirm this mapping first
--   );
-- ============================================================================
