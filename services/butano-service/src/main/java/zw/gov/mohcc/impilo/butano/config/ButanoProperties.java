package zw.gov.mohcc.impilo.butano.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the BUTANO service.
 *
 * <p>Bound from the {@code butano.*} namespace in application.yml.
 * Controls tenant enforcement, PII prevention, terminology validation,
 * reconciliation batching, provenance stamping, and outbox polling.</p>
 */
@ConfigurationProperties(prefix = "butano")
public class ButanoProperties {

    private Tenant tenant = new Tenant();
    private Pii pii = new Pii();
    private Terminology terminology = new Terminology();
    private Reconciliation reconciliation = new Reconciliation();
    private Provenance provenance = new Provenance();
    private Outbox outbox = new Outbox();

    // ── Getters / setters ──────────────────────────────────────────────

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Pii getPii() { return pii; }
    public void setPii(Pii pii) { this.pii = pii; }

    public Terminology getTerminology() { return terminology; }
    public void setTerminology(Terminology terminology) { this.terminology = terminology; }

    public Reconciliation getReconciliation() { return reconciliation; }
    public void setReconciliation(Reconciliation reconciliation) { this.reconciliation = reconciliation; }

    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    // ── Nested configuration classes ───────────────────────────────────

    /**
     * Tenant isolation settings.
     *
     * <p>{@code enforce}: when true, every FHIR resource is tagged with the tenant ID
     * and reads are filtered to only return resources for the requesting tenant.</p>
     * <p>{@code tagSystem}: the FHIR tag system URI used for tenant tagging
     * (default: "https://impilo.gov.zw/tenant").</p>
     */
    public static class Tenant {
        private boolean enforce = true;
        private String tagSystem = "https://impilo.gov.zw/tenant";

        public boolean isEnforce() { return enforce; }
        public void setEnforce(boolean enforce) { this.enforce = enforce; }

        public String getTagSystem() { return tagSystem; }
        public void setTagSystem(String tagSystem) { this.tagSystem = tagSystem; }
    }

    /**
     * PII prevention settings.
     *
     * <p>{@code enforce}: when true, the PII prevention interceptor actively rejects
     * any Patient resource containing name, telecom, address, photo, or contact
     * fields. Only CPID identifiers are permitted.</p>
     */
    public static class Pii {
        private boolean enforce = true;

        public boolean isEnforce() { return enforce; }
        public void setEnforce(boolean enforce) { this.enforce = enforce; }
    }

    /**
     * Terminology validation settings.
     *
     * <p>When enabled, coded elements (Condition.code, Observation.code, etc.)
     * are validated against the ZIBO terminology service before storage.</p>
     *
     * <p>Mode controls strictness:</p>
     * <ul>
     *   <li>STRICT — invalid codes cause a 422 rejection</li>
     *   <li>LENIENT — invalid codes generate a warning but are stored</li>
     * </ul>
     */
    public static class Terminology {
        private boolean validationEnabled = false;
        private String ziboBaseUrl = "http://localhost:8085";
        private TerminologyMode mode = TerminologyMode.LENIENT;

        public boolean isValidationEnabled() { return validationEnabled; }
        public void setValidationEnabled(boolean validationEnabled) { this.validationEnabled = validationEnabled; }

        public String getZiboBaseUrl() { return ziboBaseUrl; }
        public void setZiboBaseUrl(String ziboBaseUrl) { this.ziboBaseUrl = ziboBaseUrl; }

        public TerminologyMode getMode() { return mode; }
        public void setMode(TerminologyMode mode) { this.mode = mode; }
    }

    /**
     * Terminology validation mode.
     */
    public enum TerminologyMode {
        /** Reject resources with invalid codes (HTTP 422) */
        STRICT,
        /** Log warning but allow storage */
        LENIENT
    }

    /**
     * Reconciliation settings for O-CPID to CPID rewrite operations.
     *
     * <p>{@code batchSize}: number of resources to update in each batch during
     * reconciliation (to avoid overwhelming the database).</p>
     * <p>{@code provisionalTagSystem}: tag system for marking provisional resources.</p>
     * <p>{@code provisionalTagCode}: tag code for provisional markers.</p>
     */
    public static class Reconciliation {
        private int batchSize = 100;
        private String provisionalTagSystem = "https://impilo.gov.zw/provisional";
        private String provisionalTagCode = "provisional";

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public String getProvisionalTagSystem() { return provisionalTagSystem; }
        public void setProvisionalTagSystem(String provisionalTagSystem) { this.provisionalTagSystem = provisionalTagSystem; }

        public String getProvisionalTagCode() { return provisionalTagCode; }
        public void setProvisionalTagCode(String provisionalTagCode) { this.provisionalTagCode = provisionalTagCode; }
    }

    /**
     * Provenance stamping settings.
     *
     * <p>{@code tagSystem}: the FHIR tag system URI used for provenance metadata
     * (actor, facility, correlation, purpose, etc.).</p>
     */
    public static class Provenance {
        private String tagSystem = "https://impilo.gov.zw/provenance";

        public String getTagSystem() { return tagSystem; }
        public void setTagSystem(String tagSystem) { this.tagSystem = tagSystem; }
    }

    /**
     * Event outbox polling settings.
     *
     * <p>{@code pollIntervalMs}: how often the outbox publisher polls for
     * unpublished events (in milliseconds).</p>
     */
    public static class Outbox {
        private long pollIntervalMs = 2000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }
}
