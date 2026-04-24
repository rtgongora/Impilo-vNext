package zw.gov.mohcc.impilo.tshepo.api.biometric;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.persistence.BiometricPolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.persistence.BiometricPolicyRuleRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates {@link BiometricPolicyEvaluateRequest} against persisted {@link BiometricPolicyRuleEntity}
 * rows. First non-wildcard dimension matches win; higher {@code priority} breaks ties.
 *
 * <p>Safe default when nothing matches: {@code PROHIBITED} with no capabilities enabled.</p>
 */
@Service
public class BiometricPolicyEvaluationService {

    private final BiometricPolicyRuleRepository ruleRepository;

    public BiometricPolicyEvaluationService(BiometricPolicyRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public BiometricPolicyEvaluationResponse evaluate(BiometricPolicyEvaluateRequest request) {
        if (request == null || blank(request.subjectType()) || blank(request.workflowType())
                || blank(request.contextType()) || blank(request.biometricIntent())) {
            return denied("Missing required fields on biometric policy request");
        }

        String subject = request.subjectType().trim().toUpperCase(Locale.ROOT);
        String workflow = request.workflowType().trim().toUpperCase(Locale.ROOT);
        String context = request.contextType().trim().toUpperCase(Locale.ROOT);
        String intent = request.biometricIntent().trim().toUpperCase(Locale.ROOT);
        String modality = request.modality() == null || request.modality().isBlank()
                ? null
                : request.modality().trim().toUpperCase(Locale.ROOT);
        String requestActor =
                request.actorType() == null || request.actorType().isBlank()
                        ? null
                        : request.actorType().trim().toUpperCase(Locale.ROOT);
        Integer assurance = request.assuranceLevel();

        List<BiometricPolicyRuleEntity> rules = ruleRepository.findByActiveFlagTrueOrderByPriorityDesc();
        List<BiometricPolicyRuleEntity> candidates = new ArrayList<>();
        for (BiometricPolicyRuleEntity r : rules) {
            if (!r.isActiveFlag()) {
                continue;
            }
            if (!eqOrWildcard(r.getSubjectType(), subject)) {
                continue;
            }
            if (!eqOrWildcard(r.getWorkflowType(), workflow)) {
                continue;
            }
            if (!eqOrWildcard(r.getContextType(), context)) {
                continue;
            }
            if (!intentEquals(r.getBiometricIntent(), intent)) {
                continue;
            }
            if (r.getModality() != null && !r.getModality().isBlank()) {
                if (modality == null || !r.getModality().equalsIgnoreCase(modality)) {
                    continue;
                }
            }
            if (!actorMatches(r, requestActor)) {
                continue;
            }
            if (!assuranceMatches(r, assurance)) {
                continue;
            }
            candidates.add(r);
        }

        if (candidates.isEmpty()) {
            return denied("No biometric policy rule matched the request");
        }

        BiometricPolicyRuleEntity best = candidates.stream()
                .max(Comparator.comparingInt(BiometricPolicyRuleEntity::getPriority)
                        .thenComparing(BiometricPolicyRuleEntity::getId))
                .orElseThrow();

        String outcome = best.getPolicyOutcome().toUpperCase(Locale.ROOT);
        boolean prohibited = "PROHIBITED".equals(outcome);
        List<String> reasons = new ArrayList<>();
        if (best.getNotes() != null && !best.getNotes().isBlank()) {
            reasons.add(best.getNotes());
        }

        if (prohibited) {
            reasons.add("Policy outcome is PROHIBITED for this workflow/context/intent");
            return new BiometricPolicyEvaluationResponse(
                    "PROHIBITED",
                    false,
                    false,
                    false,
                    false,
                    best.isFallbackAllowed(),
                    best.getId(),
                    reasons);
        }

        return new BiometricPolicyEvaluationResponse(
                outcome,
                best.isEnrollmentAllowed(),
                best.isVerificationAllowed(),
                best.isIdentificationAllowed(),
                best.isDedupSupportAllowed(),
                best.isFallbackAllowed(),
                best.getId(),
                reasons.isEmpty() ? List.of() : reasons);
    }

    private static BiometricPolicyEvaluationResponse denied(String reason) {
        return new BiometricPolicyEvaluationResponse(
                "PROHIBITED", false, false, false, false, true, null, List.of(reason));
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

    private static boolean intentEquals(String ruleIntent, String actualIntent) {
        if (ruleIntent == null) {
            return false;
        }
        return ruleIntent.trim().toUpperCase(Locale.ROOT).equals(actualIntent);
    }

    private static boolean actorMatches(BiometricPolicyRuleEntity r, String requestActor) {
        String rv = r.getActorType();
        if (rv == null || rv.isBlank()) {
            return true;
        }
        String ra = rv.trim().toUpperCase(Locale.ROOT);
        if ("*".equals(ra)) {
            return true;
        }
        if (requestActor == null) {
            return false;
        }
        return ra.equals(requestActor);
    }

    private static boolean assuranceMatches(BiometricPolicyRuleEntity r, Integer loa) {
        Short min = r.getMinAssuranceLevel();
        Short max = r.getMaxAssuranceLevel();
        if (min == null && max == null) {
            return true;
        }
        if (loa == null) {
            return false;
        }
        if (min != null && loa < min) {
            return false;
        }
        return max == null || loa <= max;
    }
}
