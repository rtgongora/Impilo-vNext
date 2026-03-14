package zw.gov.mohcc.impilo.companion.consistency;

/**
 * Client interface for calling the TSHEPO PDP (Policy Decision Point).
 *
 * <p>Used by {@link ConsistencyClassFilter} for Class A enforcement when
 * policy decision headers are not already present (gateway did not inject them).</p>
 *
 * <p>Implementations call TSHEPO's /v1/authorize endpoint and return the decision.</p>
 */
public interface PdpClient {

    /**
     * Request a policy decision from TSHEPO PDP.
     *
     * @param tenantId      the tenant identifier
     * @param actorId       the actor performing the action
     * @param action        the action being performed
     * @param resourceType  the resource type being accessed
     * @param correlationId the correlation ID for tracing
     * @return the policy decision result
     * @throws PdpUnavailableException if the PDP cannot be reached
     */
    PdpDecision evaluate(String tenantId, String actorId, String action,
                          String resourceType, String correlationId);

    /**
     * Result of a PDP evaluation.
     */
    record PdpDecision(
            String decision,       // ALLOW, DENY, STEP_UP_REQUIRED
            String policyVersion,
            String reasonCode,
            String obligations
    ) {
        public boolean isAllow() {
            return "ALLOW".equalsIgnoreCase(decision);
        }
    }

    class PdpUnavailableException extends RuntimeException {
        public PdpUnavailableException(String message) {
            super(message);
        }
        public PdpUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
