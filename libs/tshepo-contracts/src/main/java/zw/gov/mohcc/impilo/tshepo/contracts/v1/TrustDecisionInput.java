package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Objects;

/**
 * Trusted, server-derived evidence for a policy decision.
 *
 * <p>Authoritative fields must not be populated from unvalidated client-supplied headers.
 * Use {@link #rejectClientHeaderPopulation(Map)} at assembly boundaries.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrustDecisionInput(
        String contractVersion,
        HumanSubjectRef humanSubject,
        WorkloadSubject callingWorkload,
        String originalClientId,
        ServiceRequestContext destination,
        String sessionId,
        String flowId,
        IdentityAssurance identityAssurance,
        AuthenticationAssurance authenticationAssurance,
        WorkloadAssurance workloadAssurance,
        ActiveContext activeContext,
        AuthorityBinding authority,
        String tenantId,
        String facilityId,
        String councilId,
        String action,
        String resourceType,
        String resourceId,
        String sensitivityClass,
        String purposeOfUse,
        ConsentDecision consentDecision,
        LawfulBasisDecision lawfulBasisDecision,
        DelegationChain delegationChain,
        boolean emergencyBreakGlass,
        OperatingMode operatingMode,
        String policyVersion,
        String requestId,
        String traceId,
        String decisionId,
        String correlationId
) {
    public TrustDecisionInput {
        TrustContractVersion.requireV1(contractVersion);
        Objects.requireNonNull(callingWorkload, "callingWorkload");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(operatingMode, "operatingMode");
        if (identityAssurance == null) {
            identityAssurance = IdentityAssurance.unknown();
        }
        if (authenticationAssurance == null) {
            authenticationAssurance = AuthenticationAssurance.none();
        }
    }

    /**
     * Refuse to assemble authoritative trust input from raw client headers.
     * Callers must validate server-side evidence first, then use a typed builder/factory.
     */
    public static void rejectClientHeaderPopulation(Map<String, String> clientHeaders) {
        if (clientHeaders == null || clientHeaders.isEmpty()) {
            return;
        }
        throw new UnsupportedOperationException(
                "TrustDecisionInput authoritative fields must not be populated from unvalidated client headers");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HumanSubjectRef humanSubject;
        private WorkloadSubject callingWorkload;
        private String originalClientId;
        private ServiceRequestContext destination;
        private String sessionId;
        private String flowId;
        private IdentityAssurance identityAssurance;
        private AuthenticationAssurance authenticationAssurance;
        private WorkloadAssurance workloadAssurance;
        private ActiveContext activeContext;
        private AuthorityBinding authority;
        private String tenantId;
        private String facilityId;
        private String councilId;
        private String action;
        private String resourceType;
        private String resourceId;
        private String sensitivityClass;
        private String purposeOfUse;
        private ConsentDecision consentDecision;
        private LawfulBasisDecision lawfulBasisDecision;
        private DelegationChain delegationChain;
        private boolean emergencyBreakGlass;
        private OperatingMode operatingMode = OperatingMode.NORMAL;
        private String policyVersion;
        private String requestId;
        private String traceId;
        private String decisionId;
        private String correlationId;

        public Builder humanSubject(HumanSubjectRef value) { this.humanSubject = value; return this; }
        public Builder callingWorkload(WorkloadSubject value) { this.callingWorkload = value; return this; }
        public Builder originalClientId(String value) { this.originalClientId = value; return this; }
        public Builder destination(ServiceRequestContext value) { this.destination = value; return this; }
        public Builder sessionId(String value) { this.sessionId = value; return this; }
        public Builder flowId(String value) { this.flowId = value; return this; }
        public Builder identityAssurance(IdentityAssurance value) { this.identityAssurance = value; return this; }
        public Builder authenticationAssurance(AuthenticationAssurance value) {
            this.authenticationAssurance = value; return this;
        }
        public Builder workloadAssurance(WorkloadAssurance value) { this.workloadAssurance = value; return this; }
        public Builder activeContext(ActiveContext value) { this.activeContext = value; return this; }
        public Builder authority(AuthorityBinding value) { this.authority = value; return this; }
        public Builder tenantId(String value) { this.tenantId = value; return this; }
        public Builder facilityId(String value) { this.facilityId = value; return this; }
        public Builder councilId(String value) { this.councilId = value; return this; }
        public Builder action(String value) { this.action = value; return this; }
        public Builder resourceType(String value) { this.resourceType = value; return this; }
        public Builder resourceId(String value) { this.resourceId = value; return this; }
        public Builder sensitivityClass(String value) { this.sensitivityClass = value; return this; }
        public Builder purposeOfUse(String value) { this.purposeOfUse = value; return this; }
        public Builder consentDecision(ConsentDecision value) { this.consentDecision = value; return this; }
        public Builder lawfulBasisDecision(LawfulBasisDecision value) { this.lawfulBasisDecision = value; return this; }
        public Builder delegationChain(DelegationChain value) { this.delegationChain = value; return this; }
        public Builder emergencyBreakGlass(boolean value) { this.emergencyBreakGlass = value; return this; }
        public Builder operatingMode(OperatingMode value) { this.operatingMode = value; return this; }
        public Builder policyVersion(String value) { this.policyVersion = value; return this; }
        public Builder requestId(String value) { this.requestId = value; return this; }
        public Builder traceId(String value) { this.traceId = value; return this; }
        public Builder decisionId(String value) { this.decisionId = value; return this; }
        public Builder correlationId(String value) { this.correlationId = value; return this; }

        public TrustDecisionInput build() {
            return new TrustDecisionInput(
                    TrustContractVersion.V1,
                    humanSubject,
                    callingWorkload,
                    originalClientId,
                    destination,
                    sessionId,
                    flowId,
                    identityAssurance,
                    authenticationAssurance,
                    workloadAssurance,
                    activeContext,
                    authority,
                    tenantId,
                    facilityId,
                    councilId,
                    action,
                    resourceType,
                    resourceId,
                    sensitivityClass,
                    purposeOfUse,
                    consentDecision,
                    lawfulBasisDecision,
                    delegationChain,
                    emergencyBreakGlass,
                    operatingMode,
                    policyVersion,
                    requestId,
                    traceId,
                    decisionId,
                    correlationId);
        }
    }
}
