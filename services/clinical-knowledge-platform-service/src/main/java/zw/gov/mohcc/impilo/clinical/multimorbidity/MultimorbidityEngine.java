package zw.gov.mohcc.impilo.clinical.multimorbidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The multimorbidity engine required by brief.md §9 — eleven panels and seven detections over the
 * whole patient rather than one disease at a time.
 *
 * <p><strong>Why this is Java and not tabular content.</strong> Every one of the seven detections is
 * set reasoning: count the medicines, find two members of one class, intersect two condition lists,
 * compare two dates. {@code PredicateEvaluator} evaluates one flat fact map with no collection
 * operators — its {@code in} compares a scalar fact against content-listed literals, its
 * {@code atLeast} counts predicates that held rather than elements, and it has no cross-collection
 * join. "Five or more medicines" is not merely awkward there, it is inexpressible. That file is also
 * leased and shared by four engines, so extending it to suit this pack would put the RMNP, IMAM and
 * paediatric packs on a changed evaluator to serve adult medicine. The clinical knowledge stays in
 * content ({@link MultimorbidityContent}, {@link MedicineClassifier}); only the set arithmetic is
 * here.</p>
 *
 * <p><strong>The reassurance rule.</strong> A detection reports NOT_DETECTED only when every input it
 * needs was present. Otherwise it reports UNDETERMINED and says which input was missing. This screen
 * exists because single-disease views hide cross-disease harm; a screen that answered "nothing found"
 * when it could not look would hide it more convincingly than the views it replaces.</p>
 */
@Service
public class MultimorbidityEngine {

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityEngine.class);

    private final MultimorbidityContent content;
    private final MedicineClassifier classifier;

    public MultimorbidityEngine(MultimorbidityContent content, MedicineClassifier classifier) {
        this.content = content;
        this.classifier = classifier;
    }

    public MultimorbidityView assess(MultimorbidityContext ctx, LocalDate today) {
        MultimorbidityContext c = ctx == null ? MultimorbidityContext.empty() : ctx;
        LocalDate asOf = today == null ? LocalDate.now() : today;

        MedicineClassifier.Classification classification = classifier.classify(c.medicationCodes());

        List<MultimorbidityView.Panel> panels = List.of(
                activeConditions(c),
                sharedRiskFactors(c),
                conflictingRecommendations(c),
                medicineBurden(c),
                interactionRisks(c, classification),
                renalAndHepaticConstraints(c),
                functionalImpact(c),
                appointmentBurden(c, asOf),
                patientPriorities(c),
                careTeamResponsibilities(c),
                consolidatedMonitoringPlan(c));

        List<MultimorbidityView.Detection> detections = List.of(
                duplicateMedicines(c, classification),
                contradictoryTargets(c),
                unsafeCombinations(c, classification),
                excessiveVisitSchedule(c, asOf),
                repeatedInvestigations(c, asOf),
                competingDietaryAdvice(c),
                highTreatmentBurden(c, asOf));

        String note = completenessNote(panels, detections);
        log.info("multimorbidity.assessed panels={} unknownPanels={} detected={} undetermined={}",
                panels.size(),
                panels.stream().filter(p -> p.state() == MultimorbidityView.PanelState.UNKNOWN).count(),
                detections.stream().filter(d -> d.state() == MultimorbidityView.DetectionState.DETECTED).count(),
                detections.stream().filter(d -> d.state() == MultimorbidityView.DetectionState.UNDETERMINED).count());

        return new MultimorbidityView(panels, detections, content.version(), content.approvalStatus(), note);
    }

    // ---------------------------------------------------------------- panels

    private MultimorbidityView.Panel activeConditions(MultimorbidityContext c) {
        if (c.conditions() == null) {
            return MultimorbidityView.Panel.unknown("active_conditions", "Active conditions",
                    "The problem list could not be read. This panel is empty because we could not look, "
                    + "not because the patient has no conditions.");
        }
        return MultimorbidityView.Panel.known("active_conditions", "Active conditions",
                c.conditions().stream().map(x -> x.display() == null || x.display().isBlank()
                        ? x.code() : x.display() + " (" + x.code() + ")").toList());
    }

    private MultimorbidityView.Panel sharedRiskFactors(MultimorbidityContext c) {
        if (c.conditions() == null) {
            return MultimorbidityView.Panel.unknown("shared_risk_factors", "Shared risk factors",
                    "The problem list could not be read.");
        }
        // Deliberately narrow: only the shared drivers this pack can name from the problem list
        // itself. A richer risk model needs the social and behavioural history, which is not wired
        // here — and inventing a risk factor is worse than showing few.
        Set<String> shared = new LinkedHashSet<>();
        if (matchesAny(c.conditions(), List.of("I10", "HYPERTENS")) && matchesAny(c.conditions(), List.of("E11", "E10", "DIABET"))) {
            shared.add("Hypertension and diabetes share cardiovascular and renal risk — one risk assessment, not two.");
        }
        if (matchesAny(c.conditions(), List.of("N18", "CKD", "CHRONIC KIDNEY")) && matchesAny(c.conditions(), List.of("E11", "E10", "DIABET", "I10", "HYPERTENS"))) {
            shared.add("The kidney disease is most likely driven by the diabetes or hypertension — treating the driver is the renal treatment.");
        }
        if (matchesAny(c.conditions(), List.of("J44", "COPD")) && matchesAny(c.conditions(), List.of("I25", "ISCHAEMIC HEART", "I50", "HEART FAILURE"))) {
            shared.add("Smoking is the shared driver of the airways and coronary disease — cessation acts on both.");
        }
        return MultimorbidityView.Panel.known("shared_risk_factors", "Shared risk factors", List.copyOf(shared));
    }

    private MultimorbidityView.Panel conflictingRecommendations(MultimorbidityContext c) {
        if (c.conditions() == null) {
            return MultimorbidityView.Panel.unknown("conflicting_recommendations", "Conflicting recommendations",
                    "The problem list could not be read.");
        }
        return MultimorbidityView.Panel.known("conflicting_recommendations", "Conflicting recommendations",
                matchedConflicts(c, null).stream().map(MultimorbidityContent.ConditionConflict::message).toList());
    }

    private MultimorbidityView.Panel medicineBurden(MultimorbidityContext c) {
        if (c.medicationCodes() == null) {
            return MultimorbidityView.Panel.unknown("medicine_burden", "Medicine burden",
                    "The medicine list could not be obtained, so the count is unknown — not zero.");
        }
        int count = c.medicationCodes().size();
        int threshold = content.threshold("polypharmacyMedicineCount", 5);
        List<String> entries = new ArrayList<>();
        entries.add(count + " regular medicine(s).");
        if (count >= threshold) {
            entries.add("At or above the review threshold of " + threshold + ". This is a prompt to "
                        + "review, not a limit — some patients need more.");
        }
        return MultimorbidityView.Panel.known("medicine_burden", "Medicine burden", entries);
    }

    private MultimorbidityView.Panel interactionRisks(MultimorbidityContext c,
                                                     MedicineClassifier.Classification classification) {
        if (c.medicationCodes() == null) {
            return MultimorbidityView.Panel.unknown("interaction_risks", "Interaction risks",
                    "The medicine list could not be obtained.");
        }
        List<String> entries = new ArrayList<>(unsafeFindings(classification).stream()
                .map(MultimorbidityView.Finding::message).toList());
        if (!classification.unclassified().isEmpty()) {
            entries.add("Not all medicines could be recognised ("
                        + String.join(", ", classification.unclassified())
                        + "), so this check is partial.");
        }
        return MultimorbidityView.Panel.known("interaction_risks", "Interaction risks", entries);
    }

    private MultimorbidityView.Panel renalAndHepaticConstraints(MultimorbidityContext c) {
        List<String> entries = new ArrayList<>();
        if (c.egfr() == null) {
            entries.add("Renal function: not available. Renal dose adjustments cannot be checked.");
        } else {
            entries.add("eGFR " + c.egfr() + " mL/min/1.73m².");
        }
        if (c.hepaticImpairment() == null || c.hepaticImpairment().isBlank()) {
            // Honest about a known estate gap rather than rendering a reassuring blank: no governed
            // Child-Pugh instrument exists, so this is null for every patient in production today.
            entries.add("Hepatic function: not scored. No governed hepatic-impairment instrument exists "
                        + "yet, so this is unknown rather than normal.");
        } else {
            entries.add("Hepatic impairment: " + c.hepaticImpairment() + ".");
        }
        // KNOWN because the panel states exactly what is and is not available; the unknowns are its
        // content, not its absence.
        return MultimorbidityView.Panel.known("renal_hepatic_constraints", "Renal and hepatic constraints", entries);
    }

    private MultimorbidityView.Panel functionalImpact(MultimorbidityContext c) {
        if (c.functionalLimitations() == null) {
            return MultimorbidityView.Panel.unknown("functional_impact", "Functional impact",
                    "Functional status has not been assessed. Not assessed is not the same as intact.");
        }
        return MultimorbidityView.Panel.known("functional_impact", "Functional impact", c.functionalLimitations());
    }

    private MultimorbidityView.Panel appointmentBurden(MultimorbidityContext c, LocalDate asOf) {
        if (c.appointments() == null) {
            return MultimorbidityView.Panel.unknown("appointment_burden", "Appointment burden",
                    "The appointment book could not be read, so the visit burden is unknown — not none.");
        }
        int window = content.threshold("visitBurdenWindowDays", 90);
        List<String> entries = new ArrayList<>();
        for (MultimorbidityContext.Appointment a : inWindow(c.appointments(), asOf, window)) {
            entries.add(a.date() + " — " + (a.clinic() == null ? "clinic not stated" : a.clinic())
                        + (a.reason() == null || a.reason().isBlank() ? "" : " (" + a.reason() + ")"));
        }
        return MultimorbidityView.Panel.known("appointment_burden", "Appointment burden", entries);
    }

    private MultimorbidityView.Panel patientPriorities(MultimorbidityContext c) {
        if (c.patientPriorities() == null) {
            return MultimorbidityView.Panel.unknown("patient_priorities", "Patient priorities",
                    "The patient has not been asked what matters most to them. §9 requires a "
                    + "person-centred prioritised plan, and it cannot be person-centred without this.");
        }
        return MultimorbidityView.Panel.known("patient_priorities", "Patient priorities", c.patientPriorities());
    }

    private MultimorbidityView.Panel careTeamResponsibilities(MultimorbidityContext c) {
        if (c.careTeam() == null) {
            return MultimorbidityView.Panel.unknown("care_team", "Care-team responsibilities",
                    "Care-team responsibilities are not recorded. Where nobody is named, everyone "
                    + "assumes somebody else is monitoring.");
        }
        return MultimorbidityView.Panel.known("care_team", "Care-team responsibilities",
                c.careTeam().stream()
                        .map(m -> m.role() + (m.name() == null || m.name().isBlank() ? "" : " — " + m.name())
                                  + (m.responsibleFor() == null || m.responsibleFor().isBlank()
                                     ? " (responsibility not stated)" : ": " + m.responsibleFor()))
                        .toList());
    }

    private MultimorbidityView.Panel consolidatedMonitoringPlan(MultimorbidityContext c) {
        if (c.conditions() == null || c.investigations() == null) {
            return MultimorbidityView.Panel.unknown("monitoring_plan", "Consolidated monitoring plan",
                    "A consolidated plan needs both the problem list and the investigation history; at "
                    + "least one could not be read.");
        }
        Map<String, LocalDate> latest = new LinkedHashMap<>();
        for (MultimorbidityContext.Investigation i : c.investigations()) {
            if (i.code() == null || i.date() == null) {
                continue;
            }
            String key = i.code().toUpperCase(Locale.ROOT);
            LocalDate seen = latest.get(key);
            if (seen == null || i.date().isAfter(seen)) {
                latest.put(key, i.date());
            }
        }
        List<String> entries = new ArrayList<>();
        latest.forEach((code, date) -> entries.add(code + " — last done " + date));
        if (entries.isEmpty()) {
            entries.add("No investigations recorded in the history that was read.");
        }
        return MultimorbidityView.Panel.known("monitoring_plan", "Consolidated monitoring plan", entries);
    }

    // ------------------------------------------------------------ detections

    private MultimorbidityView.Detection duplicateMedicines(MultimorbidityContext c,
                                                            MedicineClassifier.Classification classification) {
        String key = "duplicate_medicines";
        String title = "Duplicate medicines";
        if (c.medicationCodes() == null) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The medicine list could not be obtained.");
        }
        List<MultimorbidityView.Finding> findings = new ArrayList<>();
        for (MedicineClassifier.TherapeuticClass tc : classifier.classes()) {
            if (!tc.duplicateWithin() || classification.countIn(tc.className()) < 2) {
                continue;
            }
            List<String> members = classification.byClass().get(tc.className());
            findings.add(new MultimorbidityView.Finding(
                    "MM_DUPLICATE_" + tc.className(),
                    "HIGH",
                    "Two or more " + tc.display() + " medicines are active: " + String.join(", ", members) + ".",
                    "Duplication within a therapeutic class is almost always unintended — typically a "
                    + "switch where the previous agent was never stopped, or two prescribers each "
                    + "treating a different condition with the same class.",
                    "Confirm which agent is intended, stop the other, and record why."));
        }
        if (!findings.isEmpty()) {
            return MultimorbidityView.Detection.detected(key, title, findings);
        }
        if (!classification.complete()) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "Not every medicine could be recognised, so duplication cannot be ruled out.");
        }
        return MultimorbidityView.Detection.clear(key, title);
    }

    private MultimorbidityView.Detection contradictoryTargets(MultimorbidityContext c) {
        return conditionConflictDetection(c, "CONTRADICTORY_TARGETS",
                "contradictory_targets", "Contradictory targets");
    }

    private MultimorbidityView.Detection competingDietaryAdvice(MultimorbidityContext c) {
        return conditionConflictDetection(c, "COMPETING_DIETARY_ADVICE",
                "competing_dietary_advice", "Competing dietary advice");
    }

    private MultimorbidityView.Detection conditionConflictDetection(MultimorbidityContext c, String kind,
                                                                   String key, String title) {
        if (c.conditions() == null) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The problem list could not be read.");
        }
        if (!content.isLoaded()) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The multimorbidity content could not be loaded — a system fault, not a clean result.");
        }
        List<MultimorbidityView.Finding> findings = matchedConflicts(c, kind).stream()
                .map(x -> new MultimorbidityView.Finding(x.code(), x.severity(), x.message(),
                        x.explanation(), x.requiredAction()))
                .toList();
        return findings.isEmpty()
                ? MultimorbidityView.Detection.clear(key, title)
                : MultimorbidityView.Detection.detected(key, title, findings);
    }

    private MultimorbidityView.Detection unsafeCombinations(MultimorbidityContext c,
                                                            MedicineClassifier.Classification classification) {
        String key = "unsafe_combinations";
        String title = "Unsafe combinations";
        if (c.medicationCodes() == null) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The medicine list could not be obtained.");
        }
        List<MultimorbidityView.Finding> findings = unsafeFindings(classification);
        if (!findings.isEmpty()) {
            return MultimorbidityView.Detection.detected(key, title, findings);
        }
        if (!classification.complete()) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "Not every medicine could be recognised, so an unsafe combination cannot be ruled out.");
        }
        return MultimorbidityView.Detection.clear(key, title);
    }

    private MultimorbidityView.Detection excessiveVisitSchedule(MultimorbidityContext c, LocalDate asOf) {
        String key = "excessive_visit_schedule";
        String title = "Excessive visit schedule";
        if (c.appointments() == null) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The appointment book could not be read.");
        }
        int window = content.threshold("visitBurdenWindowDays", 90);
        int limit = content.threshold("visitBurdenAppointmentCount", 4);
        List<MultimorbidityContext.Appointment> inWindow = inWindow(c.appointments(), asOf, window);
        // Same-day attendances at different clinics are one trip, and counting them separately would
        // penalise exactly the co-ordination this detector is asking for.
        long distinctDays = inWindow.stream().map(MultimorbidityContext.Appointment::date).distinct().count();
        if (distinctDays < limit) {
            return MultimorbidityView.Detection.clear(key, title);
        }
        return MultimorbidityView.Detection.detected(key, title, List.of(new MultimorbidityView.Finding(
                "MM_VISIT_BURDEN",
                "MODERATE",
                distinctDays + " separate attendances in " + window + " days across "
                + inWindow.stream().map(MultimorbidityContext.Appointment::clinic).distinct().count() + " clinic(s).",
                "Each clinic books its own follow-up and none of them sees the others' bookings. The "
                + "cost falls entirely on the patient — transport, lost income, and the visits they "
                + "quietly stop attending.",
                "Align the follow-ups into fewer visits where the clinical intervals allow it, and "
                + "record which clinic leads.")));
    }

    private MultimorbidityView.Detection repeatedInvestigations(MultimorbidityContext c, LocalDate asOf) {
        String key = "repeated_investigations";
        String title = "Repeated investigations";
        if (c.investigations() == null) {
            return MultimorbidityView.Detection.undetermined(key, title,
                    "The investigation history could not be read.");
        }
        Map<String, Integer> intervals = new LinkedHashMap<>();
        for (MultimorbidityContent.RepeatInterval r : content.repeatIntervals()) {
            intervals.put(r.investigation(), r.minimumRepeatDays());
        }
        Map<String, List<MultimorbidityContext.Investigation>> byCode = new LinkedHashMap<>();
        for (MultimorbidityContext.Investigation i : c.investigations()) {
            if (i.code() == null || i.date() == null) {
                continue;
            }
            byCode.computeIfAbsent(i.code().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(i);
        }
        List<MultimorbidityView.Finding> findings = new ArrayList<>();
        List<String> ungoverned = new ArrayList<>();
        byCode.forEach((code, list) -> {
            Integer minDays = intervals.get(code);
            if (minDays == null) {
                if (list.size() > 1) {
                    ungoverned.add(code);
                }
                return;
            }
            List<MultimorbidityContext.Investigation> sorted = list.stream()
                    .sorted((a, b) -> a.date().compareTo(b.date())).toList();
            for (int n = 1; n < sorted.size(); n++) {
                long gap = ChronoUnit.DAYS.between(sorted.get(n - 1).date(), sorted.get(n).date());
                if (gap < minDays) {
                    findings.add(new MultimorbidityView.Finding(
                            "MM_REPEAT_" + code,
                            "LOW",
                            code + " was repeated after " + gap + " day(s); the shortest interval that "
                            + "can show a change is " + minDays + ".",
                            "Ordered by " + orderers(sorted.get(n - 1), sorted.get(n)) + ". A repeat "
                            + "inside the biological interval cannot show a change that has not had "
                            + "time to happen, so it costs the patient a needle and the system a test "
                            + "without being able to answer anything.",
                            "Check whether the earlier result answers the question before repeating."));
                }
            }
        });
        if (!findings.isEmpty()) {
            return MultimorbidityView.Detection.detected(key, title, findings);
        }
        if (!ungoverned.isEmpty()) {
            // Repeats we can see but have no governed interval for. Saying "no repeats" here would be
            // a claim the content cannot support.
            return MultimorbidityView.Detection.undetermined(key, title,
                    "Repeats were seen for " + String.join(", ", ungoverned) + ", but no minimum repeat "
                    + "interval is governed for them, so whether they were premature cannot be judged.");
        }
        return MultimorbidityView.Detection.clear(key, title);
    }

    private MultimorbidityView.Detection highTreatmentBurden(MultimorbidityContext c, LocalDate asOf) {
        String key = "high_treatment_burden";
        String title = "High treatment burden";
        List<String> missing = new ArrayList<>();
        if (c.medicationCodes() == null) {
            missing.add("the medicine list");
        }
        if (c.appointments() == null) {
            missing.add("the appointment book");
        }
        if (c.conditions() == null) {
            missing.add("the problem list");
        }
        if (!missing.isEmpty()) {
            // A burden score built on two of three sources understates the burden, and understating it
            // is the direction that leaves the patient carrying it.
            return MultimorbidityView.Detection.undetermined(key, title,
                    "Burden is the sum of medicines, visits and conditions; " + String.join(" and ", missing)
                    + " could not be read, so any score would understate it.");
        }
        int medThreshold = content.threshold("polypharmacyMedicineCount", 5);
        int visitThreshold = content.threshold("visitBurdenAppointmentCount", 4);
        int conditionThreshold = content.threshold("conditionCountForMultimorbidity", 2);
        int window = content.threshold("visitBurdenWindowDays", 90);
        int high = content.threshold("treatmentBurdenScoreHigh", 6);

        long visits = inWindow(c.appointments(), asOf, window).stream()
                .map(MultimorbidityContext.Appointment::date).distinct().count();
        long longTerm = c.conditions().stream().filter(MultimorbidityContext.Condition::longTerm).count();

        int score = Math.max(0, c.medicationCodes().size() - medThreshold)
                    + (int) Math.max(0, visits - visitThreshold)
                    + (int) Math.max(0, longTerm - (conditionThreshold + 1));
        if (score < high) {
            return MultimorbidityView.Detection.clear(key, title);
        }
        return MultimorbidityView.Detection.detected(key, title, List.of(new MultimorbidityView.Finding(
                "MM_TREATMENT_BURDEN",
                "MODERATE",
                "Treatment burden score " + score + " (threshold " + high + "): "
                + c.medicationCodes().size() + " medicine(s), " + visits + " attendance(s) in "
                + window + " days, " + longTerm + " long-term condition(s).",
                "Treatment burden is what the patient is being asked to carry, and it accumulates one "
                + "reasonable decision at a time. Beyond a point the plan stops being followed, and "
                + "the part that gets dropped is chosen by the patient rather than by the clinician.",
                "Build one consolidated, prioritised plan with the patient, starting from what they "
                + "said matters most, and deprescribe or align what does not serve it.")));
    }

    // ------------------------------------------------------------- internals

    private List<MultimorbidityView.Finding> unsafeFindings(MedicineClassifier.Classification classification) {
        List<MultimorbidityView.Finding> findings = new ArrayList<>();
        for (MedicineClassifier.UnsafeCombination combo : classifier.unsafeCombinations()) {
            if (!comboMatches(combo, classification)) {
                continue;
            }
            findings.add(new MultimorbidityView.Finding(combo.code(), combo.severity(), combo.message(),
                    combo.explanation(), combo.requiredAction()));
        }
        return findings;
    }

    private boolean comboMatches(MedicineClassifier.UnsafeCombination combo,
                                 MedicineClassifier.Classification classification) {
        if (combo.withinClassCount() != null) {
            // A combination against itself — two anticoagulants — needs a count, not a set.
            String only = combo.classes().isEmpty() ? null : combo.classes().get(0);
            return only != null && classification.countIn(only) >= combo.withinClassCount();
        }
        if (allPresent(combo.classes(), classification)) {
            return true;
        }
        for (List<String> alternative : combo.alsoSatisfiedBy()) {
            if (allPresent(alternative, classification)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allPresent(List<String> classes, MedicineClassifier.Classification classification) {
        return !classes.isEmpty() && classes.stream().allMatch(classification::has);
    }

    /** @param kind null to match any kind */
    private List<MultimorbidityContent.ConditionConflict> matchedConflicts(MultimorbidityContext c, String kind) {
        if (c.conditions() == null) {
            return List.of();
        }
        List<MultimorbidityContent.ConditionConflict> out = new ArrayList<>();
        for (MultimorbidityContent.ConditionConflict conflict : content.conflicts()) {
            if (kind != null && !kind.equals(conflict.kind())) {
                continue;
            }
            if (matchesAny(c.conditions(), conflict.conditionsA())
                && matchesAny(c.conditions(), conflict.conditionsB())) {
                out.add(conflict);
            }
        }
        return out;
    }

    private static boolean matchesAny(List<MultimorbidityContext.Condition> conditions, List<String> fragments) {
        for (MultimorbidityContext.Condition condition : conditions) {
            String code = condition.code() == null ? "" : condition.code().toUpperCase(Locale.ROOT);
            String display = condition.display() == null ? "" : condition.display().toUpperCase(Locale.ROOT);
            for (String fragment : fragments) {
                String f = fragment.toUpperCase(Locale.ROOT);
                if ((!code.isEmpty() && code.startsWith(f)) || (!display.isEmpty() && display.contains(f))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<MultimorbidityContext.Appointment> inWindow(
            List<MultimorbidityContext.Appointment> appointments, LocalDate asOf, int windowDays) {
        List<MultimorbidityContext.Appointment> out = new ArrayList<>();
        for (MultimorbidityContext.Appointment a : appointments) {
            if (a.date() == null) {
                continue;
            }
            long days = Math.abs(ChronoUnit.DAYS.between(a.date(), asOf));
            if (days <= windowDays) {
                out.add(a);
            }
        }
        return out;
    }

    private static String orderers(MultimorbidityContext.Investigation first,
                                   MultimorbidityContext.Investigation second) {
        String a = first.orderedBy() == null || first.orderedBy().isBlank() ? "an unrecorded requester" : first.orderedBy();
        String b = second.orderedBy() == null || second.orderedBy().isBlank() ? "an unrecorded requester" : second.orderedBy();
        return a.equals(b) ? a + " twice" : a + " then " + b;
    }

    private static String completenessNote(List<MultimorbidityView.Panel> panels,
                                           List<MultimorbidityView.Detection> detections) {
        List<String> unknownPanels = panels.stream()
                .filter(p -> p.state() == MultimorbidityView.PanelState.UNKNOWN)
                .map(MultimorbidityView.Panel::title).toList();
        List<String> undetermined = detections.stream()
                .filter(d -> d.state() == MultimorbidityView.DetectionState.UNDETERMINED)
                .map(MultimorbidityView.Detection::title).toList();
        if (unknownPanels.isEmpty() && undetermined.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("This view is partial. ");
        if (!unknownPanels.isEmpty()) {
            sb.append("Could not be read: ").append(String.join(", ", unknownPanels)).append(". ");
        }
        if (!undetermined.isEmpty()) {
            sb.append("Not answered: ").append(String.join(", ", undetermined)).append(". ");
        }
        sb.append("An unanswered check is not a clear one.");
        return sb.toString();
    }
}
