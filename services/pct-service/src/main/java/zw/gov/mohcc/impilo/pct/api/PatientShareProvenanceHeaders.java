package zw.gov.mohcc.impilo.pct.api;

/**
 * Optional HTTP headers for patient-mediated external provider collaboration provenance.
 * Populated by VITO (or BFF) when creating clinical artifacts from a governed share grant.
 */
public final class PatientShareProvenanceHeaders {

    public static final String GRANT_ID = "X-Patient-Share-Grant-Id";
    public static final String CONTRIBUTION_ID = "X-Vito-Contribution-Id";
    public static final String TEMP_PROVIDER_PUBLIC_ID = "X-Temporary-Provider-Public-Id";
    /** Original workflow correlation from VITO patient_share_request.correlation_id */
    public static final String SHARE_CORRELATION_ID = "X-Patient-Share-Correlation-Id";
    public static final String TRUST_LEVEL = "X-External-Provider-Trust-Level";

    private PatientShareProvenanceHeaders() {}
}
