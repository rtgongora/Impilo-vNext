package zw.gov.mohcc.impilo.companion.consistency;

/**
 * Consistency classes per the v1.1 companion spec.
 *
 * <ul>
 *   <li><b>CLASS_A</b>: Sync PDP decision required, OR valid offline entitlement with freshness proof.
 *       If PDP unavailable → 503 PDP_UNAVAILABLE (break-glass only if action allows).</li>
 *   <li><b>CLASS_B</b>: Bounded stale allowed with staleness evidence and reconciliation path.
 *       Staleness threshold per domain; exceeding → 409 STALE_CONTEXT.</li>
 *   <li><b>CLASS_C</b>: Offline allowed with entitlement, strict audit, post-sync reconciliation.
 *       Requires Offline-Entitlement token; action queued to offline pipeline.</li>
 * </ul>
 */
public enum ConsistencyClass {

    CLASS_A("Synchronous PDP decision required"),
    CLASS_B("Bounded staleness with reconciliation"),
    CLASS_C("Offline with entitlement and replay");

    private final String description;

    ConsistencyClass(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
