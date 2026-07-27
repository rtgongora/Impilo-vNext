package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.AppropriatenessDtos.*;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureDefinitionEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ProcedureDefinitionRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Appropriateness and duplication detection (pipeline §5).
 *
 * <p>The governing rule of this class is the specification's: <b>do not silently cancel</b>.
 * No detection here produces a cancellation, and none produces a bare refusal. Every one
 * carries a disposition — ask for clarification, propose a safe alternative, or block — and a
 * suggested action naming what the requester should do next. A rejection with no next step is
 * how a request disappears, which is the failure §4 and §5 exist together to prevent.</p>
 *
 * <p>The second rule is that an undeclared input is reported as unknown rather than assumed.
 * A procedure with no declared duplication window is not a procedure that can never be a
 * duplicate; it is a procedure nobody has decided about, and saying so is more useful than a
 * confident silence. False detections are not a cheap error either: they teach clinicians to
 * override the engine, and an engine that gets overridden by habit protects nobody when it is
 * finally right.</p>
 */
@Service
public class AppropriatenessEngine {

    private final ProcedureDefinitionRepository definitions;

    public AppropriatenessEngine(ProcedureDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public AppropriatenessVerdict evaluate(UUID tenantId, AppropriatenessRequest request) {
        List<Detection> detections = new ArrayList<>();

        Optional<ProcedureDefinitionEntity> maybe = definitions
                .findByTenantIdAndDefinitionCodeAndStatus(tenantId, request.definitionCode(), "PUBLISHED");
        if (maybe.isEmpty()) {
            // Not a detection about the patient — a detection about the request itself.
            detections.add(new Detection("UNKNOWN_PROCEDURE", "BLOCK", "HIGH",
                    "No published catalogue entry for '" + request.definitionCode() + "'",
                    "Check the procedure code, or ask the catalogue governance group to publish it."));
            return verdict(detections);
        }
        ProcedureDefinitionEntity def = maybe.get();

        detectIdentity(request, detections);
        detectSiteAndSide(def, request, detections);
        detectDuplicateAndRecentEquivalent(def, request, detections);
        detectConflicting(def, request, detections);
        detectMissingPrerequisite(def, request, detections);
        detectAgeAndPregnancy(def, request, detections);
        detectFacilityAndResources(def, request, detections);
        detectUnsafeTiming(def, request, detections);

        return verdict(detections);
    }

    /** §5 wrong patient. The requester asserts an identity match; absence of the assertion is not a match. */
    private void detectIdentity(AppropriatenessRequest r, List<Detection> out) {
        if (!Boolean.TRUE.equals(r.patientIdentityConfirmed())) {
            out.add(new Detection("PATIENT_IDENTITY_UNCONFIRMED", "CLARIFY", "HIGH",
                    "Patient identity has not been confirmed for this request.",
                    "Confirm identity against the record before the request proceeds."));
        }
    }

    /**
     * §5 wrong site and wrong side.
     *
     * <p>For a lateralised procedure a missing side is a BLOCK, not a warning. The
     * lateralised procedures done fastest and with least supervision — chest drain,
     * thoracostomy, central line, thoracotomy, escharotomy, burr hole — are exactly where
     * wrong-side harm happens, and they are done at the bedside by a lone clinician with no
     * second checker and no marking step. A warning is something you click past.</p>
     */
    private void detectSiteAndSide(ProcedureDefinitionEntity def, AppropriatenessRequest r, List<Detection> out) {
        boolean lateralised = "LATERALISED".equals(def.getLateralityApplicability())
                || "MULTI_SITE".equals(def.getLateralityApplicability());
        if (lateralised && isBlank(r.side())) {
            out.add(new Detection("SIDE_NOT_SPECIFIED", "BLOCK", "HIGH",
                    def.getClinicalName() + " is lateralised and no side was given.",
                    "Record the side. This cannot be waived by an emergency override."));
        }
        if (!lateralised && !isBlank(r.side()) && !"NOT_APPLICABLE".equalsIgnoreCase(r.side())) {
            // A side on a midline procedure means the requester believes something the
            // catalogue does not. One of the two is wrong and it is worth finding out which.
            out.add(new Detection("SIDE_ON_NON_LATERALISED_PROCEDURE", "CLARIFY", "MEDIUM",
                    "A side was given for " + def.getClinicalName() + ", which the catalogue records as "
                            + def.getLateralityApplicability() + ".",
                    "Confirm the intended procedure and site — the request or the catalogue entry is wrong."));
        }
        if (!isBlank(def.getAnatomicalSite()) && !isBlank(r.anatomicalSite())
                && !def.getAnatomicalSite().equalsIgnoreCase(r.anatomicalSite())) {
            out.add(new Detection("SITE_MISMATCH", "CLARIFY", "HIGH",
                    "Requested site '" + r.anatomicalSite() + "' differs from the catalogue site '"
                            + def.getAnatomicalSite() + "'.",
                    "Confirm the site, or select the procedure that matches it."));
        }
    }

    /** §5 duplicate request and recent equivalent procedure. */
    private void detectDuplicateAndRecentEquivalent(ProcedureDefinitionEntity def,
                                                    AppropriatenessRequest r, List<Detection> out) {
        if (Boolean.TRUE.equals(r.openRequestExists())) {
            out.add(new Detection("DUPLICATE_OPEN_REQUEST", "CLARIFY", "MEDIUM",
                    "An open request for " + def.getClinicalName() + " already exists for this patient.",
                    "Use the existing request, or state why a second is needed."));
        }
        Integer window = def.getDuplicateLookbackDays();
        LocalDate last = r.lastCompletedOn();
        if (last == null) {
            return;
        }
        if (window == null) {
            // Reported, not guessed. An undeclared window is a decision nobody has made.
            out.add(new Detection("DUPLICATE_WINDOW_UNDECLARED", "CLARIFY", "LOW",
                    def.getClinicalName() + " was last done on " + last
                            + " and the catalogue declares no repeat window.",
                    "Confirm the repeat is intended; ask governance to declare a window for this procedure."));
            return;
        }
        long days = ChronoUnit.DAYS.between(last, r.requestedFor() == null ? LocalDate.now() : r.requestedFor());
        if (days < window) {
            out.add(new Detection("RECENT_EQUIVALENT_PROCEDURE", "CLARIFY", "MEDIUM",
                    def.getClinicalName() + " was completed " + days + " days ago; the declared repeat window is "
                            + window + " days.",
                    "Confirm a repeat is clinically indicated, or review the previous result first."));
        }
    }

    /** §5 conflicting procedure. */
    private void detectConflicting(ProcedureDefinitionEntity def, AppropriatenessRequest r, List<Detection> out) {
        for (String other : safe(r.otherOpenProcedureCodes())) {
            if (def.getConflictsWith() != null && def.getConflictsWith().contains(other)) {
                out.add(new Detection("CONFLICTING_PROCEDURE", "PROPOSE_ALTERNATIVE", "MEDIUM",
                        def.getClinicalName() + " conflicts with an open request for " + other + ".",
                        "Do one or the other: keep the broader procedure and withdraw the narrower one, "
                                + "recording a reason and a next action on whichever is withdrawn."));
            }
        }
    }

    /** §5 missing prerequisite. */
    private void detectMissingPrerequisite(ProcedureDefinitionEntity def,
                                           AppropriatenessRequest r, List<Detection> out) {
        for (String required : safe(def.getPrerequisiteCodes())) {
            if (!safe(r.completedProcedureCodes()).contains(required)) {
                out.add(new Detection("MISSING_PREREQUISITE", "CLARIFY", "HIGH",
                        def.getClinicalName() + " normally follows " + required + ", which is not recorded as done.",
                        "Complete or record " + required + " first, or state why it is not needed for this patient."));
            }
        }
    }

    /** §5 contraindication — the age and pregnancy constraints the catalogue can answer directly. */
    private void detectAgeAndPregnancy(ProcedureDefinitionEntity def,
                                       AppropriatenessRequest r, List<Detection> out) {
        Integer ageDays = r.patientAgeDays();
        if (ageDays != null) {
            if (def.getAgeMinDays() != null && ageDays < def.getAgeMinDays()) {
                out.add(new Detection("AGE_BELOW_RANGE", "PROPOSE_ALTERNATIVE", "HIGH",
                        "Patient is " + ageDays + " days old; " + def.getClinicalName()
                                + " is declared from " + def.getAgeMinDays() + " days.",
                        "Select the age-appropriate variant of this procedure, or seek specialist advice."));
            }
            if (def.getAgeMaxDays() != null && ageDays > def.getAgeMaxDays()) {
                out.add(new Detection("AGE_ABOVE_RANGE", "PROPOSE_ALTERNATIVE", "HIGH",
                        "Patient is " + ageDays + " days old; " + def.getClinicalName()
                                + " is declared to " + def.getAgeMaxDays() + " days.",
                        "Select the adult variant of this procedure, or seek specialist advice."));
            }
        }
        String preg = def.getPregnancyApplicability();
        if (Boolean.TRUE.equals(r.pregnant())) {
            if ("CONTRAINDICATED".equals(preg)) {
                out.add(new Detection("CONTRAINDICATED_IN_PREGNANCY", "BLOCK", "HIGH",
                        def.getClinicalName() + " is contraindicated in pregnancy.",
                        "Seek specialist advice and consider an alternative; if it must proceed, "
                                + "record an explicit clinical justification."));
            } else if ("CAUTION".equals(preg)) {
                out.add(new Detection("CAUTION_IN_PREGNANCY", "CLARIFY", "MEDIUM",
                        def.getClinicalName() + " carries a pregnancy caution.",
                        "Confirm the benefit outweighs the risk and record the decision."));
            }
        } else if (r.pregnant() == null && "REQUIRES_TEST".equals(preg)) {
            out.add(new Detection("PREGNANCY_STATUS_UNKNOWN", "CLARIFY", "HIGH",
                    def.getClinicalName() + " requires pregnancy status to be established.",
                    "Perform or record a pregnancy test before the procedure proceeds."));
        }
    }

    /** §5 inappropriate facility, specialist unavailable, equipment unavailable. */
    private void detectFacilityAndResources(ProcedureDefinitionEntity def,
                                            AppropriatenessRequest r, List<Detection> out) {
        String setting = r.setting();
        if (!isBlank(setting) && def.getPermittedSettings() != null && !def.getPermittedSettings().isEmpty()
                && !def.getPermittedSettings().contains(setting)) {
            out.add(new Detection("SETTING_NOT_PERMITTED", "PROPOSE_ALTERNATIVE", "HIGH",
                    def.getClinicalName() + " is not performed in " + setting
                            + "; permitted settings are " + def.getPermittedSettings() + ".",
                    "Move the procedure to a permitted setting, or refer to a facility that provides it."));
        }
        if (Boolean.FALSE.equals(r.facilitySupportsProcedure())) {
            out.add(new Detection("INAPPROPRIATE_FACILITY", "PROPOSE_ALTERNATIVE", "HIGH",
                    "This facility does not provide " + def.getClinicalName() + ".",
                    "Refer to a facility with the capability. Do not cancel the request — "
                            + "route it, so the patient stays on somebody's list."));
        }
        if (Boolean.FALSE.equals(r.requiredSpecialistAvailable())) {
            out.add(new Detection("REQUIRED_SPECIALIST_UNAVAILABLE", "PROPOSE_ALTERNATIVE", "MEDIUM",
                    "No available operator privileged for " + def.getOwningSpecialty() + ".",
                    "Schedule when a privileged operator is available, arrange supervision, or refer."));
        }
        if (Boolean.FALSE.equals(r.requiredEquipmentAvailable())) {
            out.add(new Detection("REQUIRED_EQUIPMENT_UNAVAILABLE", "PROPOSE_ALTERNATIVE", "MEDIUM",
                    "Equipment required for " + def.getClinicalName() + " is not available.",
                    "Defer with a date, or refer. Record a next action either way."));
        }
    }

    /** §5 unsafe timing. */
    private void detectUnsafeTiming(ProcedureDefinitionEntity def, AppropriatenessRequest r, List<Detection> out) {
        if (r.requestedFor() != null && r.requestedFor().isBefore(LocalDate.now())) {
            out.add(new Detection("REQUESTED_IN_THE_PAST", "CLARIFY", "LOW",
                    "The requested date " + r.requestedFor() + " is in the past.",
                    "Correct the date, or record this as a retrospective entry."));
        }
        if (Boolean.TRUE.equals(r.onAnticoagulant()) && requiresCoagulationCare(def)) {
            out.add(new Detection("ANTICOAGULATION_UNRESOLVED", "CLARIFY", "HIGH",
                    "Patient is anticoagulated and " + def.getClinicalName() + " carries a bleeding risk.",
                    "Record the anticoagulation plan — held, bridged or accepted — before proceeding."));
        }
    }

    private boolean requiresCoagulationCare(ProcedureDefinitionEntity def) {
        // Derived from what the catalogue declares rather than a hardcoded procedure list, so a
        // newly catalogued invasive procedure inherits the check without a code change.
        return def.getRequirements() != null && def.getRequirements().stream()
                .anyMatch(req -> "ANTICOAGULATION".equals(req.getRequirementKind())
                        || "LABORATORY".equals(req.getRequirementKind()));
    }

    /**
     * A verdict is never a cancellation. The strongest outcome is BLOCKED, which means "not as
     * requested" and always carries the actions that would unblock it.
     */
    private AppropriatenessVerdict verdict(List<Detection> detections) {
        String outcome = detections.stream().anyMatch(d -> "BLOCK".equals(d.disposition()))
                ? "BLOCKED"
                : detections.isEmpty() ? "APPROPRIATE" : "PROCEED_WITH_CLARIFICATION";
        return new AppropriatenessVerdict(outcome, detections.size(), detections);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static List<String> safe(List<String> in) { return in == null ? List.of() : in; }

    @SuppressWarnings("unused")
    private static String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }
}
