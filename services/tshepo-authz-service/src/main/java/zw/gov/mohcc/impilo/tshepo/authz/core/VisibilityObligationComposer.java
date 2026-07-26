package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.EscalationGrantView;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.Obligations;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ClinicalAccessLevel;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ExportPolicy;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PiiAccessLevel;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Composes {@link Obligations} including {@link VisibilityProfile} from purpose-of-use,
 * optional matched policy rule JSON, resource sensitivity, and optional workflow grant.
 */
public final class VisibilityObligationComposer {

    private VisibilityObligationComposer() {
    }

    /**
     * Compose obligations without any entitlement to specially-protected content. Callers that
     * have made a confidentiality decision should use the six-argument overload instead.
     */
    public static Obligations compose(
            AuthzInternalRequest request,
            PurposeOfUse purpose,
            int riskScore,
            PolicyRuleEntity matchedAllowRule,
            Optional<EscalationGrantView> activeGrant,
            ObjectMapper objectMapper) {
        return compose(request, purpose, riskScore, matchedAllowRule, activeGrant, objectMapper, null);
    }

    /**
     * @param grantedConfidentialCategories the PolicyEngine's confidentiality verdict — which
     *        categories of {@code SPECIALLY_PROTECTED} content this requester may receive
     *        ({@code ["*"]} for a whole-set grant). Empty or null revokes every category the rule
     *        overlay may have asked for, so protected content stays withheld by default rather than
     *        by hope. Confidentiality rides this obligation, not the tier: a tier is a total order,
     *        and holding safeguarding must not imply holding mental health.
     */
    public static Obligations compose(
            AuthzInternalRequest request,
            PurposeOfUse purpose,
            int riskScore,
            PolicyRuleEntity matchedAllowRule,
            Optional<EscalationGrantView> activeGrant,
            ObjectMapper objectMapper,
            List<String> grantedConfidentialCategories) {

        String loggingLevel = riskScore > 50 ? "ELEVATED" : "STANDARD";

        BaseObligation base = purposeDefaults(purpose, loggingLevel);
        VisibilityProfile.Builder vis = VisibilityProfile.builder(base.profile);

        if (matchedAllowRule != null && matchedAllowRule.getConditions() != null
                && !matchedAllowRule.getConditions().isBlank()) {
            mergeRuleVisibility(matchedAllowRule.getConditions(), vis, objectMapper);
        }

        DataSensitivityClass sensitivity = ResourceSensitivityClassifier.classifyResource(request.resourceType());
        vis.resourceSensitivityClass(sensitivity.name());

        DataVisibilityTier resourceCap = ResourceSensitivityClassifier.maxTierForResource(request.resourceType());
        vis.capVisibilityTier(resourceCap);

        if (activeGrant.isPresent()) {
            EscalationGrantView g = activeGrant.get();
            DataVisibilityTier grantTier = DataVisibilityTier.fromString(g.visibilityCeiling());
            vis.liftWithEscalation(grantTier, g.grantToken().toString(), g.workflowType());
        }

        if (request.workflowContext() != null && !request.workflowContext().isBlank()) {
            vis.workflowContext(request.workflowContext());
        }

        // Applied LAST, after the rule overlay and any escalation lift, because either can add
        // categories and this is the one revocation that has to win. An empty grant revokes
        // everything: the default for every actor is that no protected content is reachable.
        if (grantedConfidentialCategories == null || grantedConfidentialCategories.isEmpty()) {
            vis.revokeConfidentialCategories();
        } else {
            vis.grantConfidentialCategories(grantedConfidentialCategories);
        }

        VisibilityProfile profile = vis.build();
        Obligations o = new Obligations(
                base.maxScope,
                base.maskFields,
                base.loggingLevel,
                base.consentScopeRef,
                profile);
        return enforceExportForSensitiveActions(request, o);
    }

    private static Obligations enforceExportForSensitiveActions(AuthzInternalRequest request, Obligations o) {
        String action = request.action();
        if (action != null && action.toUpperCase().contains("EXPORT")) {
            VisibilityProfile vp = o.visibilityProfile();
            if (vp == null) {
                return o;
            }
            if (Boolean.TRUE.equals(vp.aggregateOnly())) {
                return new Obligations(o.maxScope(), o.maskFields(), o.loggingLevel(), o.consentScopeRef(),
                        new VisibilityProfile(
                                vp.visibilityTier(),
                                vp.piiAccess(),
                                vp.clinicalAccess(),
                                true,
                                vp.resourceSensitivityClass(),
                                vp.escalationGrantId(),
                                vp.workflowContext(),
                                vp.suppressFields(),
                                vp.pseudonymiseFields(),
                                ExportPolicy.AGGREGATE_ONLY.name(),
                                false,
                                vp.confidentialCategories()));
            }
        }
        return o;
    }

    private static void mergeRuleVisibility(String conditionsJson, VisibilityProfile.Builder vis,
                                            ObjectMapper objectMapper) {
        try {
            Map<String, Object> map = objectMapper.readValue(conditionsJson, new TypeReference<>() {});
            Object v = map.get("visibility");
            if (!(v instanceof Map<?, ?> vm)) {
                return;
            }
            if (vm.get("visibilityTier") != null) {
                vis.visibilityTier(vm.get("visibilityTier").toString());
            }
            if (vm.get("piiAccess") != null) {
                vis.piiAccess(vm.get("piiAccess").toString());
            }
            if (vm.get("clinicalAccess") != null) {
                vis.clinicalAccess(vm.get("clinicalAccess").toString());
            }
            if (vm.get("aggregateOnly") instanceof Boolean b) {
                vis.aggregateOnly(b);
            }
            if (vm.get("exportPolicy") != null) {
                vis.exportPolicy(vm.get("exportPolicy").toString());
            }
            if (vm.get("suppressFields") instanceof List<?> sf) {
                List<String> paths = new ArrayList<>();
                for (Object o : sf) {
                    paths.add(o.toString());
                }
                vis.suppressFields(paths);
            }
            if (vm.get("drillDownAllowed") instanceof Boolean d) {
                vis.drillDownAllowed(d);
            }
            if (vm.get("confidentialCategories") instanceof List<?> cc) {
                List<String> categories = new ArrayList<>();
                for (Object o : cc) {
                    categories.add(o.toString());
                }
                vis.grantConfidentialCategories(categories);
            }
        } catch (Exception ignored) {
            // ignore malformed overlay
        }
    }

    private record BaseObligation(
            String maxScope,
            List<String> maskFields,
            String loggingLevel,
            String consentScopeRef,
            VisibilityProfile profile
    ) {}

    private static BaseObligation purposeDefaults(PurposeOfUse purpose, String loggingLevel) {
        return switch (purpose) {
            case RESEARCH -> new BaseObligation(
                    "ANONYMIZED",
                    List.of("name", "phone", "address", "dateOfBirth", "nationalId", "givenName", "familyName", "email"),
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.DEIDENTIFIED_ROW_LEVEL.name())
                            .piiAccess(PiiAccessLevel.MASKED.name())
                            .clinicalAccess(ClinicalAccessLevel.SUMMARY.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.REDACTED.name())
                            .drillDownAllowed(false)
                            .buildBare());
            case PUBLIC_HEALTH -> new BaseObligation(
                    "PSEUDONYMIZED",
                    List.of("name", "phone", "nationalId", "givenName", "familyName", "email"),
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.PSEUDONYMISED_PERSON_LEVEL.name())
                            .piiAccess(PiiAccessLevel.MASKED.name())
                            .clinicalAccess(ClinicalAccessLevel.NONE.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.REDACTED.name())
                            .drillDownAllowed(false)
                            .buildBare());
            case PAYMENT -> new BaseObligation(
                    "FACILITY_SCOPE",
                    List.of("phone"),
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY.name())
                            .piiAccess(PiiAccessLevel.LIMITED.name())
                            .clinicalAccess(ClinicalAccessLevel.NONE.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.FULL_AUDITED.name())
                            .drillDownAllowed(true)
                            .buildBare());
            case OPERATIONS -> new BaseObligation(
                    "FACILITY_SCOPE",
                    List.of("name", "phone", "nationalId"),
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY.name())
                            .piiAccess(PiiAccessLevel.LIMITED.name())
                            .clinicalAccess(ClinicalAccessLevel.NONE.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.REDACTED.name())
                            .drillDownAllowed(true)
                            .buildBare());
            case EMERGENCY, BREAK_GLASS -> new BaseObligation(
                    null,
                    null,
                    "ELEVATED",
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.name())
                            .piiAccess(PiiAccessLevel.FULL.name())
                            .clinicalAccess(ClinicalAccessLevel.FULL.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.FULL_AUDITED.name())
                            .drillDownAllowed(true)
                            .buildBare());
            // HL7 ActReason models coordination-of-care (COC) as a specialization of TREAT, so
            // it shares TREATMENT's envelope. The codes stay distinct so the decision log can
            // separate care-team/referral/write-back traffic from direct care at the point of use.
            case TREATMENT, CARE_COORDINATION -> new BaseObligation(
                    "PATIENT",
                    null,
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.name())
                            .piiAccess(PiiAccessLevel.FULL.name())
                            .clinicalAccess(ClinicalAccessLevel.FULL.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.FULL_AUDITED.name())
                            .drillDownAllowed(true)
                            .buildBare());
            // A person on their own record: full sight of their own data, but no export or
            // drill-down rights — those are clinician/analyst affordances, not citizen ones.
            // maxScope stays "PATIENT" (the existing subject-bound token services already
            // understand) rather than inventing a "SELF" scope no downstream service reads.
            case SELF_SERVICE -> new BaseObligation(
                    "PATIENT",
                    null,
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.name())
                            .piiAccess(PiiAccessLevel.FULL.name())
                            .clinicalAccess(ClinicalAccessLevel.FULL.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.REDACTED.name())
                            .drillDownAllowed(false)
                            .buildBare());
            // Statutory oversight: identity and operational facts about the regulated party,
            // never their patients' clinical detail. clinicalAccess NONE is the whole point of
            // giving regulatory duty its own code — see PurposeOfUse.REGULATORY_DUTY.
            case REGULATORY_DUTY -> new BaseObligation(
                    "FACILITY_SCOPE",
                    List.of("nationalId"),
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY.name())
                            .piiAccess(PiiAccessLevel.LIMITED.name())
                            .clinicalAccess(ClinicalAccessLevel.NONE.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.FULL_AUDITED.name())
                            .drillDownAllowed(true)
                            .buildBare());
            case SYSTEM -> new BaseObligation(
                    "SYSTEM",
                    null,
                    loggingLevel,
                    null,
                    VisibilityProfile.builder()
                            .visibilityTier(DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY.name())
                            .piiAccess(PiiAccessLevel.FULL.name())
                            .clinicalAccess(ClinicalAccessLevel.FULL.name())
                            .aggregateOnly(false)
                            .exportPolicy(ExportPolicy.FULL_AUDITED.name())
                            .drillDownAllowed(true)
                            .buildBare());
        };
    }
}
