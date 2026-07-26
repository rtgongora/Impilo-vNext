package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ClinicalAccessLevel;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ExportPolicy;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PiiAccessLevel;

import java.util.List;

/**
 * Data visibility and representation policy for one authorized request.
 * Serialized inside {@link Obligations} and mirrored as flat trust headers for PEPs.
 *
 * <h2>Three orthogonal axes</h2>
 * <p>{@code visibilityTier} is an ordered ladder of <em>how much</em> may be disclosed.
 * {@code piiAccess} and {@code clinicalAccess} are separate because they are separate questions.
 * {@code confidentialCategories} is the third such axis: <em>which categories of specially-protected
 * content</em> this requester may receive.</p>
 *
 * <p>It is deliberately NOT a rung on the tier ladder. A tier is a total order, so a
 * "specially-protected" tier above {@code FULL_IDENTIFIED_CLINICAL} would assert that reaching
 * confidential content implies reaching everything below it — which is wrong for the two cases that
 * matter: a safeguarding lead who should read the safeguarding disclosure but not the full clinical
 * record, and a sexual-health nurse who should not thereby reach the mental-health notes.
 * Confidentiality is also <em>relational</em> ("protected from the guardian, not from the person"),
 * which is not a disclosure level at all.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisibilityProfile(
        String visibilityTier,
        String piiAccess,
        String clinicalAccess,
        Boolean aggregateOnly,
        String resourceSensitivityClass,
        String escalationGrantId,
        String workflowContext,
        List<String> suppressFields,
        List<String> pseudonymiseFields,
        String exportPolicy,
        Boolean drillDownAllowed,
        /**
         * Categories of {@code SPECIALLY_PROTECTED} content this requester may receive (see the
         * governed confidential-category value set). Null or empty means none — the default, so
         * protected content is withheld unless a decision explicitly granted the category.
         */
        List<String> confidentialCategories
) {
    /**
     * Pre-confidentiality arity, kept so the existing construction sites (and any deserialiser
     * pinned to the old shape) compile and behave unchanged: no granted categories.
     */
    public VisibilityProfile(
            String visibilityTier,
            String piiAccess,
            String clinicalAccess,
            Boolean aggregateOnly,
            String resourceSensitivityClass,
            String escalationGrantId,
            String workflowContext,
            List<String> suppressFields,
            List<String> pseudonymiseFields,
            String exportPolicy,
            Boolean drillDownAllowed) {
        this(visibilityTier, piiAccess, clinicalAccess, aggregateOnly, resourceSensitivityClass,
                escalationGrantId, workflowContext, suppressFields, pseudonymiseFields,
                exportPolicy, drillDownAllowed, null);
    }

    /**
     * Whether this requester may receive content in the given confidential category.
     * Fail-closed: no granted categories means no protected content.
     */
    public boolean allowsConfidentialCategory(String category) {
        if (category == null || category.isBlank()
                || confidentialCategories == null || confidentialCategories.isEmpty()) {
            return false;
        }
        String wanted = category.trim().toUpperCase(java.util.Locale.ROOT);
        for (String granted : confidentialCategories) {
            if (granted == null) {
                continue;
            }
            String g = granted.trim().toUpperCase(java.util.Locale.ROOT);
            // "*" is the whole-set grant used for self-access and the audited emergency waiver,
            // where enumerating categories would add nothing but a chance to miss one.
            if (g.equals("*") || g.equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any protected content at all is reachable by this requester. */
    public boolean allowsAnyConfidentialCategory() {
        return confidentialCategories != null && !confidentialCategories.isEmpty();
    }

    public static VisibilityProfile legacyUnrestricted() {
        return new VisibilityProfile(
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public static VisibilityProfile aggregateSupervision() {
        return new VisibilityProfile(
                "AGGREGATE_ONLY",
                "NONE",
                "NONE",
                true,
                "AGGREGATE_SENSITIVE",
                null,
                null,
                null,
                null,
                "AGGREGATE_ONLY",
                false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(VisibilityProfile seed) {
        Builder b = new Builder();
        if (seed != null) {
            b.visibilityTier = seed.visibilityTier();
            b.piiAccess = seed.piiAccess();
            b.clinicalAccess = seed.clinicalAccess();
            b.aggregateOnly = seed.aggregateOnly();
            b.resourceSensitivityClass = seed.resourceSensitivityClass();
            b.escalationGrantId = seed.escalationGrantId();
            b.workflowContext = seed.workflowContext();
            b.suppressFields = seed.suppressFields();
            b.pseudonymiseFields = seed.pseudonymiseFields();
            b.exportPolicy = seed.exportPolicy();
            b.drillDownAllowed = seed.drillDownAllowed();
            b.confidentialCategories = seed.confidentialCategories();
        }
        return b;
    }

    public static final class Builder {
        private String visibilityTier;
        private String piiAccess;
        private String clinicalAccess;
        private Boolean aggregateOnly;
        private String resourceSensitivityClass;
        private String escalationGrantId;
        private String workflowContext;
        private List<String> suppressFields;
        private List<String> pseudonymiseFields;
        private String exportPolicy;
        private Boolean drillDownAllowed;
        private List<String> confidentialCategories;

        public Builder visibilityTier(String v) {
            this.visibilityTier = v;
            return this;
        }

        public Builder piiAccess(String v) {
            this.piiAccess = v;
            return this;
        }

        public Builder clinicalAccess(String v) {
            this.clinicalAccess = v;
            return this;
        }

        public Builder aggregateOnly(Boolean v) {
            this.aggregateOnly = v;
            return this;
        }

        public Builder resourceSensitivityClass(String v) {
            this.resourceSensitivityClass = v;
            return this;
        }

        public Builder escalationGrantId(String v) {
            this.escalationGrantId = v;
            return this;
        }

        public Builder workflowContext(String v) {
            this.workflowContext = v;
            return this;
        }

        public Builder suppressFields(List<String> paths) {
            this.suppressFields = paths;
            return this;
        }

        public Builder pseudonymiseFields(List<String> paths) {
            this.pseudonymiseFields = paths;
            return this;
        }

        public Builder exportPolicy(String v) {
            this.exportPolicy = v;
            return this;
        }

        public Builder drillDownAllowed(Boolean v) {
            this.drillDownAllowed = v;
            return this;
        }

        public Builder confidentialCategories(List<String> v) {
            this.confidentialCategories = v;
            return this;
        }

        public VisibilityProfile buildBare() {
            return new VisibilityProfile(
                    visibilityTier, piiAccess, clinicalAccess, aggregateOnly,
                    resourceSensitivityClass, escalationGrantId, workflowContext,
                    suppressFields, pseudonymiseFields, exportPolicy, drillDownAllowed,
                    confidentialCategories);
        }

        public void capVisibilityTier(DataVisibilityTier cap) {
            if (visibilityTier == null) {
                visibilityTier = cap.name();
                return;
            }
            DataVisibilityTier current = DataVisibilityTier.fromString(visibilityTier);
            DataVisibilityTier capped = DataVisibilityTier.min(current, cap);
            visibilityTier = capped.name();
            if (capped == DataVisibilityTier.AGGREGATE_ONLY) {
                aggregateOnly = true;
                clinicalAccess = ClinicalAccessLevel.NONE.name();
                piiAccess = PiiAccessLevel.NONE.name();
                exportPolicy = ExportPolicy.AGGREGATE_ONLY.name();
                drillDownAllowed = false;
            }
        }

        /**
         * Grant confidential categories, unioned with anything already granted. A grant of
         * {@code "*"} means every category (self-access, audited emergency waiver).
         */
        public void grantConfidentialCategories(List<String> categories) {
            if (categories == null || categories.isEmpty()) {
                return;
            }
            List<String> merged = new java.util.ArrayList<>();
            if (confidentialCategories != null) {
                merged.addAll(confidentialCategories);
            }
            for (String c : categories) {
                if (c != null && !c.isBlank() && !merged.contains(c)) {
                    merged.add(c);
                }
            }
            confidentialCategories = List.copyOf(merged);
        }

        /**
         * Withdraw every confidential-category grant. The clamp applied when a decision found no
         * entitlement — it must run last, because a rule overlay or an escalation grant can add
         * categories and this is the one revocation that has to win.
         */
        public void revokeConfidentialCategories() {
            confidentialCategories = null;
        }

        public void liftWithEscalation(DataVisibilityTier grantCeiling, String grantToken, String workflow) {
            DataVisibilityTier current = DataVisibilityTier.fromString(
                    visibilityTier != null ? visibilityTier : DataVisibilityTier.AGGREGATE_ONLY.name());
            DataVisibilityTier lifted = DataVisibilityTier.max(current, grantCeiling);
            visibilityTier = lifted.name();
            escalationGrantId = grantToken;
            workflowContext = workflow;
            if (Boolean.TRUE.equals(aggregateOnly) && lifted.disclosureLevel() > DataVisibilityTier.AGGREGATE_ONLY.disclosureLevel()) {
                aggregateOnly = false;
            }
            if (lifted.disclosureLevel() >= DataVisibilityTier.IDENTIFIED_LIMITED_CLINICAL.disclosureLevel()) {
                clinicalAccess = ClinicalAccessLevel.SUMMARY.name();
            }
            if (lifted == DataVisibilityTier.FULL_IDENTIFIED_CLINICAL) {
                clinicalAccess = ClinicalAccessLevel.FULL.name();
                piiAccess = PiiAccessLevel.FULL.name();
            }
        }

        public VisibilityProfile build() {
            return new VisibilityProfile(
                    visibilityTier, piiAccess, clinicalAccess, aggregateOnly,
                    resourceSensitivityClass, escalationGrantId, workflowContext,
                    suppressFields, pseudonymiseFields, exportPolicy, drillDownAllowed,
                    confidentialCategories);
        }
    }
}
