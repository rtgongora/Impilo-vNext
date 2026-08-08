package zw.gov.mohcc.impilo.gl.config;

import java.util.Set;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Which actor types may perform each write in the general ledger.
 *
 * <h2>What was open</h2>
 * <p>Measured 2026-08-08: of 12 write mappings, only the three on {@code GlJournalsController}
 * were guarded — the P0 register's {@code GlJournalsController actorType:0} line was already
 * stale. The other <b>nine had no authorization of any kind</b>:</p>
 *
 * <pre>
 *   POST   /internal/v1/gl/accounts                  create a GL account
 *   POST   /internal/v1/gl/accounts/seed             seed the chart of accounts
 *   PUT    /internal/v1/gl/accounts/{accountId}      amend a GL account
 *   POST   /internal/v1/gl/budgets                   create a budget
 *   POST   /internal/v1/gl/budgets/refresh-actuals   restate actuals
 *   POST   /internal/v1/gl/periods/ensure            open an accounting period
 *   POST   /internal/v1/gl/periods/{periodId}/close  CLOSE an accounting period
 *   POST   /internal/v1/gl/periods/year-end          YEAR-END close
 *   POST   /internal/v1/gl/trial-balance/snapshot    snapshot the trial balance
 * </pre>
 *
 * <p>Closing a period and running year-end are the two that decide what the national ledger says
 * happened in a financial year. The chart of accounts decides what anything can be posted against.
 * None of it is work a citizen, a patient's guardian, or a clinician acting in clinical capacity
 * has any reason to do.</p>
 *
 * <h2>Why this is a map and not nine call sites</h2>
 * <p>Same reasoning as costa's {@code MoneyWriteAuthorization}: guarding method-by-method leaves
 * the defect one edit away, because the next controller added here inherits nothing. The default
 * is {@link ActorTypeGuard#BACK_OFFICE_WRITERS} and there is no wider tier — <b>every</b> write in
 * this service decides money, so unlike costa there is no charge-capture exception. A new endpoint
 * is gated the moment it is mapped, and {@code GlWriteAuthorizationTest} fails if any mapped write
 * resolves to a duty that would admit a citizen.</p>
 *
 * <h2>Why not {@code @PreAuthorize}</h2>
 * <p>{@code @EnableMethodSecurity} is not enabled in this service, so the annotations would be
 * inert. And no role reaches a service through the trust headers — see {@code ActorTypeGuard}'s
 * KNOWN GAP. The finer "a ledger accountant, not any back-office operator" distinction is recorded
 * in {@code intendedRoles} and left visibly unenforced rather than faked.</p>
 */
final class GlWriteAuthorization {

    private GlWriteAuthorization() {
    }

    /**
     * The v1.1 conformance probe writes nothing and is driven by the in-JVM golden-contract suite,
     * which presents no actor type. Gating it would fail that suite while protecting nothing.
     */
    private static final Set<String> EXEMPT = Set.of(
            "/internal/v1/test-command",
            "/internal/v1/health");

    static boolean isExempt(String path) {
        return EXEMPT.contains(path);
    }

    /**
     * Every write in the general ledger is a money decision. Never null: an unlisted path gets the
     * same set, so a newly-mapped endpoint is gated rather than open.
     */
    static ActorTypeGuard.Duty dutyFor(String path) {
        return new ActorTypeGuard.Duty(
                "this general-ledger operation",
                ActorTypeGuard.BACK_OFFICE_WRITERS,
                // Recorded, not enforced: what this really wants is a ledger duty. No header
                // carries one today. See ActorTypeGuard's KNOWN GAP.
                Set.of("LEDGER_ACCOUNTANT", "FINANCE_CONTROLLER"));
    }
}
