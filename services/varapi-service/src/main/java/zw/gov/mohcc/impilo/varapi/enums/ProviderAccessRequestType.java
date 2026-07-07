package zw.gov.mohcc.impilo.varapi.enums;

/**
 * The kind of self-service provider-access request an applicant submits
 * (IATG Journey D / Section 4 landing choices that need a durable case).
 *
 * <p>HAVE_PROVIDER_ID / RECOVER / COUNCIL_NUMBER / EC_NUMBER are handled by the
 * existing claim/recover/evidence lanes and are recorded here only when they
 * cannot auto-resolve; NEW_PROVIDER and ORG_INVITATION always create a durable
 * request because there is no auto-issue path for them.</p>
 */
public enum ProviderAccessRequestType {
    NEW_PROVIDER,
    ORG_INVITATION,
    COUNCIL_NUMBER,
    EC_NUMBER,
    HAVE_PROVIDER_ID,
    RECOVER
}
