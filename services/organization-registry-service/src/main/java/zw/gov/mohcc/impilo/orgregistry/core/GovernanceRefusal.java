package zw.gov.mohcc.impilo.orgregistry.core;

/**
 * A governed refusal (v1.3.8 §Scope B): a safe reason code, the affected scope, and the
 * governance action required to clear it.
 *
 * <p>Deliberately not an {@code IllegalStateException}. A refused governance operation is a
 * legitimate, expected outcome carrying information the caller must act on — not a bug, and
 * never a 200 with an empty body.
 */
public class GovernanceRefusal extends RuntimeException {

    private final String reasonCode;
    private final String affectedScope;
    private final String requiredGovernanceAction;

    public GovernanceRefusal(String reasonCode, String message, String affectedScope,
                             String requiredGovernanceAction) {
        super(message);
        this.reasonCode = reasonCode;
        this.affectedScope = affectedScope;
        this.requiredGovernanceAction = requiredGovernanceAction;
    }

    public String getReasonCode() { return reasonCode; }
    public String getAffectedScope() { return affectedScope; }
    public String getRequiredGovernanceAction() { return requiredGovernanceAction; }
}
