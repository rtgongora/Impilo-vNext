package zw.gov.mohcc.impilo.tshepo.api.patientshare;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.persistence.PatientSharePolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.PatientSharePolicyRuleRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates patient-mediated share workflows against {@link PatientSharePolicyRuleEntity} rows.
 * Highest {@code priority} wins; ties broken by id.
 */
@Service
public class PatientSharePolicyEvaluationService {

    private final PatientSharePolicyRuleRepository ruleRepository;

    public PatientSharePolicyEvaluationService(PatientSharePolicyRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public PatientSharePolicyEvaluationResponse evaluate(PatientSharePolicyEvaluateRequest request) {
        if (request == null || blank(request.workflowAction()) || blank(request.scopeType())
                || blank(request.actorType())) {
            return denied("Missing required fields on patient-share policy request");
        }

        String workflow = request.workflowAction().trim().toUpperCase(Locale.ROOT);
        String scope = request.scopeType().trim().toUpperCase(Locale.ROOT);
        String actor = request.actorType().trim().toUpperCase(Locale.ROOT);
        String trust = request.trustLevel() == null || request.trustLevel().isBlank()
                ? null
                : request.trustLevel().trim();
        boolean councilSupplied = Boolean.TRUE.equals(request.councilRegistrationSupplied());
        boolean councilMatched = Boolean.TRUE.equals(request.councilRegistrationMatched());

        List<PatientSharePolicyRuleEntity> rules = ruleRepository.findByActiveFlagTrueOrderByPriorityDesc();
        List<PatientSharePolicyRuleEntity> candidates = new ArrayList<>();
        for (PatientSharePolicyRuleEntity r : rules) {
            if (!r.isActiveFlag()) {
                continue;
            }
            if (!eqOrWildcard(r.getWorkflowAction(), workflow)) {
                continue;
            }
            if (!eqOrWildcard(r.getScopePattern(), scope)) {
                continue;
            }
            if (!eqOrWildcard(r.getActorPattern(), actor)) {
                continue;
            }
            if (!PatientShareTrustLevels.meetsMinimum(trust, r.getMinTrustLevel())) {
                continue;
            }
            if (r.isRequireCouncilRegistration() && !councilSupplied) {
                continue;
            }
            if ("CONTRIBUTION_WRITE".equals(workflow)
                    && r.isPermitWrite()
                    && PatientShareTrustLevels.rank(trust) >= PatientShareTrustLevels.rank(
                            PatientShareTrustLevels.COUNCIL_NUMBER_FOUND)
                    && !councilMatched) {
                continue;
            }
            candidates.add(r);
        }

        if (candidates.isEmpty()) {
            return denied("No patient-share policy rule matched the request");
        }

        PatientSharePolicyRuleEntity best = candidates.stream()
                .max(Comparator.comparingInt(PatientSharePolicyRuleEntity::getPriority)
                        .thenComparing(PatientSharePolicyRuleEntity::getId))
                .orElseThrow();

        String outcome = best.getPolicyOutcome().trim().toUpperCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        if (best.getNotes() != null && !best.getNotes().isBlank()) {
            reasons.add(best.getNotes());
        }

        if ("PROHIBITED".equals(outcome)) {
            reasons.add("Policy outcome is PROHIBITED for this workflow/scope/trust");
            return new PatientSharePolicyEvaluationResponse(
                    "PROHIBITED",
                    false,
                    false,
                    false,
                    best.isRequireCouncilRegistration(),
                    best.getId(),
                    reasons);
        }

        boolean read = best.isPermitRead();
        boolean write = best.isPermitWrite();
        boolean temp = best.isPermitTempProviderId();

        if ("RESTRICTED".equals(outcome)) {
            write = false;
            temp = false;
            reasons.add("RESTRICTED outcome — write and temp-provider capabilities suppressed");
        }

        return new PatientSharePolicyEvaluationResponse(
                outcome,
                read,
                write,
                temp,
                best.isRequireCouncilRegistration(),
                best.getId(),
                reasons.isEmpty() ? List.of() : reasons);
    }

    private static PatientSharePolicyEvaluationResponse denied(String reason) {
        return new PatientSharePolicyEvaluationResponse(
                "PROHIBITED", false, false, false, true, null, List.of(reason));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean eqOrWildcard(String ruleValue, String actual) {
        if (ruleValue == null) {
            return false;
        }
        String rv = ruleValue.trim().toUpperCase(Locale.ROOT);
        if ("*".equals(rv)) {
            return true;
        }
        return rv.equals(actual);
    }
}
