package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pct.integration.ClinicalKnowledgeIntegration;
import zw.gov.mohcc.impilo.pct.integration.VitoIntegration;
import zw.gov.mohcc.impilo.pct.persistence.repository.ConfidentialityCapacityRepository;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConfidentialityCapacityEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves everything {@link ConfidentialityStamper} needs from outside itself: the governed policy
 * (CKP), the subject's date of birth (VITO) and any recorded mature-minor capacity (pct).
 *
 * <p>Every resolution degrades to absence rather than to a guess, and every degradation is visible in
 * the stamp's {@code basis}. That is the stamp-fails-open half of the design: losing a protection
 * class leaves a record readable by the care team, whereas inventing one makes it invisible to them.
 *
 * <p>Whether a decided protection class is actually written is governed by
 * {@code pct.confidentiality.stamp-class}, default {@code false}. Until the governance flip the class
 * is held at FULL_CLINICAL while the category is recorded truthfully.
 */
@Service
public class ConfidentialCarePolicyProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialCarePolicyProvider.class);

    /** The governed parameter codes. Their VALUES live in CKP; only the codes are known here. */
    static final String CODE_SRH_AGE = "SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS";
    static final String CODE_NON_TERMINATION_LOSS = "SRH_NON_TERMINATION_LOSS_CONFIDENTIAL_FROM_GUARDIAN";

    private final ClinicalKnowledgeIntegration clinicalKnowledge;
    private final VitoIntegration vito;
    private final ConfidentialityCapacityRepository capacities;
    private final boolean classStampingEnabled;

    public ConfidentialCarePolicyProvider(
            ClinicalKnowledgeIntegration clinicalKnowledge,
            VitoIntegration vito,
            ConfidentialityCapacityRepository capacities,
            @Value("${pct.confidentiality.stamp-class:false}") boolean classStampingEnabled) {
        this.clinicalKnowledge = clinicalKnowledge;
        this.vito = vito;
        this.capacities = capacities;
        this.classStampingEnabled = classStampingEnabled;
    }

    /** Whether a decided protection class is written to the record. False until the governance flip. */
    public boolean classStampingEnabled() {
        return classStampingEnabled;
    }

    /**
     * The governed policy as it stood on {@code asOf}. Never null — an unreachable platform yields a
     * policy that answers {@link Optional#empty()} to everything, which the stamper records as
     * {@code POLICY_UNAVAILABLE}. There is deliberately no seeded fallback age.
     */
    public ConfidentialCarePolicy policyAsOf(UUID tenantId, LocalDate asOf) {
        Map<String, Object> ageParam = safely(() ->
                clinicalKnowledge.policyParameter(tenantId, CODE_SRH_AGE, asOf), CODE_SRH_AGE);
        Map<String, Object> lossParam = safely(() ->
                clinicalKnowledge.policyParameter(tenantId, CODE_NON_TERMINATION_LOSS, asOf), CODE_NON_TERMINATION_LOSS);

        Optional<Integer> age = intValue(ageParam);
        Optional<Boolean> lossConfidential = boolValue(lossParam);
        String version = ageParam == null ? null : str(ageParam.get("contentVersion"));
        boolean ratified = ageParam != null && Boolean.TRUE.equals(ageParam.get("ratified"));

        return new ConfidentialCarePolicy() {
            @Override
            public Optional<Integer> confidentialFromGuardianAgeYears(ConfidentialityCategory category) {
                // Only SRH is governed today. Another category asking gets absence, not the SRH age —
                // the consent ages genuinely differ per domain and must not be borrowed across them.
                return category == ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH ? age : Optional.empty();
            }

            @Override
            public Optional<Boolean> nonTerminationLossConfidentialFromGuardian() {
                return lossConfidential;
            }

            @Override
            public String contentVersion() {
                return version;
            }

            @Override
            public boolean ratified() {
                return ratified;
            }
        };
    }

    /**
     * The subject's context at the act. A date of birth that cannot be resolved yields a context with
     * a null date, which the stamper records as {@code AGE_UNRESOLVED} — never a rejected write.
     */
    public ConfidentialityStamper.SubjectContext subjectAt(UUID tenantId, String cpid,
                                                           ConfidentialityCategory category, LocalDate actDate) {
        LocalDate dob = resolveDateOfBirth(cpid);
        boolean capacity = hasCapacityAsOf(tenantId, cpid, category, actDate);
        return new ConfidentialityStamper.SubjectContext(dob, capacity);
    }

    private LocalDate resolveDateOfBirth(String cpid) {
        if (cpid == null || cpid.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> patient = vito.resolvePatientByCpid(cpid);
            if (patient == null) {
                return null;
            }
            Object demographics = patient.get("demographics");
            Map<?, ?> source = demographics instanceof Map<?, ?> m ? m : patient;
            String raw = firstNonBlank(
                    str(source.get("dateOfBirth")), str(source.get("dob")), str(source.get("birthDate")));
            return raw == null ? null : LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
        } catch (RuntimeException e) {
            // Degrade, never reject: losing the stamp is recoverable, losing the record is not.
            log.warn("Could not resolve date of birth for a confidentiality stamp: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Whether a HAS_CAPACITY determination stood on the act date. The most recent determination on or
     * before that date wins; a later one cannot retrospectively explain an earlier stamp.
     */
    private boolean hasCapacityAsOf(UUID tenantId, String cpid, ConfidentialityCategory category, LocalDate actDate) {
        if (tenantId == null || cpid == null || actDate == null) {
            return false;
        }
        try {
            var found = capacities.findAsOf(tenantId, cpid, category.code(), actDate);
            return !found.isEmpty()
                    && ConfidentialityCapacityEntity.HAS_CAPACITY.equals(found.get(0).getCapacity());
        } catch (RuntimeException e) {
            log.warn("Could not read capacity determinations: {}", e.getMessage());
            return false;
        }
    }

    private static <T> T safely(java.util.function.Supplier<T> call, String what) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            log.warn("Policy parameter {} unavailable: {}", what, e.getMessage());
            return null;
        }
    }

    private static Optional<Integer> intValue(Map<String, Object> param) {
        String raw = param == null ? null : str(param.get("value"));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            log.warn("Policy parameter value '{}' is not an integer — treated as absent", raw);
            return Optional.empty();
        }
    }

    private static Optional<Boolean> boolValue(Map<String, Object> param) {
        String raw = param == null ? null : str(param.get("value"));
        if (raw == null) {
            return Optional.empty();
        }
        // Only the two literal spellings count. Anything else is absent, not false — "false" and
        // "we could not read it" must not collapse into the same answer.
        if ("true".equalsIgnoreCase(raw.trim())) {
            return Optional.of(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) {
                return v;
            }
        }
        return null;
    }
}
