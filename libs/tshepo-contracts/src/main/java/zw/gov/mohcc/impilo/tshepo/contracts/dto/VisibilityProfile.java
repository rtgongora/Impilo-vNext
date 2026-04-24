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
        Boolean drillDownAllowed
) {
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

        public VisibilityProfile buildBare() {
            return new VisibilityProfile(
                    visibilityTier, piiAccess, clinicalAccess, aggregateOnly,
                    resourceSensitivityClass, escalationGrantId, workflowContext,
                    suppressFields, pseudonymiseFields, exportPolicy, drillDownAllowed);
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
                    suppressFields, pseudonymiseFields, exportPolicy, drillDownAllowed);
        }
    }
}
