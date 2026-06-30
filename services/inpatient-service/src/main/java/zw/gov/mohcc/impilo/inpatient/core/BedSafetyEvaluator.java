package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether a candidate bed is clinically SAFE for a given patient + clinical requirements.
 *
 * <p>Pure logic, no I/O — the safety doctrine for inpatient bed placement (gender bay, age group,
 * isolation, oxygen, monitoring, ICU). A bed inherits its ward's gender/age designation and clinical
 * capabilities unless the bed overrides them. {@link #evaluate} returns the list of violations
 * (empty = safe); the caller fails-closed on any non-empty result.</p>
 */
@Component
public class BedSafetyEvaluator {

    /** What the patient/clinical context requires of a bed. */
    public record SafetyRequest(
            String patientGender,        // MALE | FEMALE | null
            Integer patientAge,          // years; null unknown
            boolean requiresIsolation,
            boolean requiresOxygen,
            boolean requiresMonitoring,
            boolean requiresIcu) {
    }

    /**
     * @return list of human-readable violation codes; empty list means the bed is safe.
     */
    public List<String> evaluate(BedEntity bed, WardEntity ward, SafetyRequest req) {
        List<String> violations = new ArrayList<>();

        // 1. Gender bay (same-sex). Bed designation overrides ward; ANY/null = no constraint.
        String bayGender = firstNonAny(bed.getGenderDesignation(),
                ward != null ? ward.getGenderDesignation() : null);
        if (bayGender != null && req.patientGender() != null
                && !bayGender.equalsIgnoreCase(req.patientGender())) {
            violations.add("GENDER_MISMATCH: bed/ward is " + bayGender
                    + " but patient is " + req.patientGender());
        }

        // 2. Age group (ward-level). PAEDIATRIC/NEONATAL wards reject adults and vice-versa.
        if (ward != null && req.patientAge() != null) {
            String ag = ward.getAgeGroup();
            if ("PAEDIATRIC".equalsIgnoreCase(ag) && req.patientAge() >= 18) {
                violations.add("AGE_GROUP_MISMATCH: paediatric ward, patient age " + req.patientAge());
            } else if ("NEONATAL".equalsIgnoreCase(ag) && req.patientAge() > 0) {
                violations.add("AGE_GROUP_MISMATCH: neonatal ward, patient age " + req.patientAge());
            } else if ("ADULT".equalsIgnoreCase(ag) && req.patientAge() < 16) {
                violations.add("AGE_GROUP_MISMATCH: adult ward, patient age " + req.patientAge());
            }
        }

        // 3-6. Clinical capability: bed capability OR ward capability must satisfy each requirement.
        if (req.requiresIsolation() && !capable(bed.isIsolationCapable(), ward, w -> w.isIsolationCapable())) {
            violations.add("ISOLATION_REQUIRED: no isolation capability at bed/ward");
        }
        if (req.requiresOxygen() && !capable(bed.isOxygenAvailable(), ward, w -> w.isOxygenAvailable())) {
            violations.add("OXYGEN_REQUIRED: no oxygen at bed/ward");
        }
        if (req.requiresMonitoring() && !capable(bed.isMonitoringCapable(), ward, w -> w.isMonitoringCapable())) {
            violations.add("MONITORING_REQUIRED: no monitoring capability at bed/ward");
        }
        if (req.requiresIcu() && !capable(bed.isIcuCapable(), ward, w -> w.isIcuCapable())) {
            violations.add("ICU_REQUIRED: bed/ward is not ICU-capable");
        }
        return violations;
    }

    private interface WardCap {
        boolean test(WardEntity w);
    }

    private boolean capable(boolean bedCap, WardEntity ward, WardCap wardCap) {
        return bedCap || (ward != null && wardCap.test(ward));
    }

    private static String firstNonAny(String bed, String ward) {
        if (bed != null && !bed.isBlank() && !"ANY".equalsIgnoreCase(bed)) {
            return bed;
        }
        if (ward != null && !ward.isBlank() && !"ANY".equalsIgnoreCase(ward)) {
            return ward;
        }
        return null;
    }
}
