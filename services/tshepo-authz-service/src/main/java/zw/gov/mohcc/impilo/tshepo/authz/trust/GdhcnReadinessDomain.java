package zw.gov.mohcc.impilo.tshepo.authz.trust;

/**
 * The fixed catalogue of GDHCN readiness domains assessed by the cockpit. Each tenant has at
 * most one assessment row per domain; unassessed domains default to {@link TrustReadiness#NOT_STARTED}.
 * The catalogue is code (not data) so the cockpit always shows the full, honest picture.
 */
public enum GdhcnReadinessDomain {
    GOVERNANCE("Governance & programme ownership", "tshepo-authz"),
    POLICY_LEGAL_PRIVACY("Policy, legal & privacy basis", "tshepo-consent / governance"),
    IDENTITY_TRUST("Identity & trust assurance", "tshepo-authz / identity-assurance"),
    PKI_KEY_MANAGEMENT("PKI & key management", "tshepo-keys"),
    CERT_ISSUANCE_REVOCATION("Certificate issuance & revocation", "tshepo-authz (DSC registry)"),
    SIGNATURE_VERIFICATION("Signature verification", "tshepo-trust-crypto"),
    TLS_SECURITY("Transport & infrastructure security", "platform / envoy"),
    AUDIT_MONITORING("Audit & monitoring", "tshepo-authz (audit outbox)"),
    IPS_FHIR_READINESS("IPS / FHIR content readiness", "butano / fhir-gateway"),
    OPS_INCIDENT("Operations & incident response", "platform ops");

    private final String title;
    private final String defaultLinkedComponent;

    GdhcnReadinessDomain(String title, String defaultLinkedComponent) {
        this.title = title;
        this.defaultLinkedComponent = defaultLinkedComponent;
    }

    public String title() {
        return title;
    }

    public String defaultLinkedComponent() {
        return defaultLinkedComponent;
    }
}
