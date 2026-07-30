package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;
import zw.gov.mohcc.impilo.clinical.maternal.IndicatorEngine;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalNearMissService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maternal near-miss identification and the two severe-outcome indicators, over HTTP.
 *
 * <p>{@link MaternalNearMissService} and the indicators have been engine-only, so the classification
 * was reachable from a test and from nowhere else. A clinician could not see whether the woman in
 * front of her met the WHO criteria, and the near-miss-to-death ratio could not be computed by
 * anything. This is the surface that makes both reachable.
 *
 * <h2>Identification, not review</h2>
 * <p>This answers "did she meet the criteria?" and stops. It neither conducts nor schedules a review.
 * MPDSR is a governed confidential instrument with its own firewall against the facility register, and
 * fusing the two here would drag a survival statistic behind a confidentiality grant.
 *
 * <h2>Not on the confidential lane, deliberately</h2>
 * <p>Near-miss identification carries no confidentiality stamp — it is ordinary clinical
 * classification. Mounting it under {@code /confidential/} would make the ratio uncomputable for the
 * people who need it and would confuse the protection MPDSR genuinely needs with one this does not.
 *
 * <h2>Nothing is persisted</h2>
 * <p>Both endpoints are POSTs that store nothing. The observations belong to the encounter that
 * recorded them and the case list belongs to the caller's query; owning a copy of either here would
 * create a second answer to what the woman's creatinine was.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/near-miss")
public class MaternalNearMissController {

    private final MaternalNearMissService nearMiss;

    public MaternalNearMissController(MaternalNearMissService nearMiss) {
        this.nearMiss = nearMiss;
    }

    /**
     * The WHO organ-dysfunction observations, exactly as {@code NearMissObservations} declares them.
     *
     * <p>Every field is boxed and every one is optional, because <b>absent means NOT RECORDED and
     * never "absent" in the clinical sense</b>. Jackson leaves an omitted field null, the service
     * injects only non-null facts, and an uninjected fact leaves its criterion unresolved so the table
     * reports INDETERMINATE instead of walking to the not-met row. Primitives here would default
     * {@code shock} to false and turn "we never assessed her for shock" into "she was not in shock" —
     * a near-miss recorded as a normal birth.
     */
    public record ObservationsRequest(
            Boolean shock, Boolean cardiacArrest, Boolean continuousVasoactiveDrugs,
            Boolean cardiopulmonaryResuscitation, Double lactateMmolL, Double phArterial,
            Boolean acuteCyanosis, Boolean gasping, Integer respiratoryRate,
            Integer intubationNotForAnaesthesiaMinutes, Boolean oxygenSaturationBelow90ForOverAnHour,
            Boolean oliguriaNonResponsiveToFluids, Integer creatinineUmolL,
            Boolean dialysisForAcuteRenalFailure,
            Boolean clottingFailure, Integer plateletsPerUl, Integer unitsTransfused,
            Boolean jaundiceWithPreEclampsia, Integer bilirubinUmolL,
            Boolean prolongedUnconsciousness, Boolean stroke, Boolean uncontrollableFits,
            Boolean totalParalysis,
            Boolean hysterectomyForInfectionOrHaemorrhage) {

        MaternalNearMissService.NearMissObservations toDomain() {
            return new MaternalNearMissService.NearMissObservations(
                    shock, cardiacArrest, continuousVasoactiveDrugs, cardiopulmonaryResuscitation,
                    lactateMmolL, phArterial,
                    acuteCyanosis, gasping, respiratoryRate, intubationNotForAnaesthesiaMinutes,
                    oxygenSaturationBelow90ForOverAnHour,
                    oliguriaNonResponsiveToFluids, creatinineUmolL, dialysisForAcuteRenalFailure,
                    clottingFailure, plateletsPerUl, unitsTransfused,
                    jaundiceWithPreEclampsia, bilirubinUmolL,
                    prolongedUnconsciousness, stroke, uncontrollableFits, totalParalysis,
                    hysterectomyForInfectionOrHaemorrhage);
        }
    }

    /**
     * Classifies one woman against the WHO criteria.
     *
     * <p>An absent body is a null request, which is a real clinical state — an encounter where nothing
     * relevant was recorded — and yields INDETERMINATE with every criterion unresolved rather than a
     * 400. The caller learns that nothing was assessed, which is what happened.
     */
    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(
            @RequestBody(required = false) ObservationsRequest request) {

        ClassificationEngine.Outcome outcome = nearMiss.classify(
                request == null ? null : request.toDomain());

        if (outcome == null) {
            // The content pack failed to load or lost its table. Not a clinical answer, and it must
            // never be presented as one: NEAR_MISS_NOT_MET here would report a missing rulebook as a
            // woman who was assessed and found well.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "near_miss_criteria_unavailable");
            body.put("message", "The WHO near-miss criteria could not be loaded, so this woman was not "
                    + "classified. This is not a finding of 'no near-miss'. Classify clinically and "
                    + "report the content failure.");
            return ResponseEntity.status(502).body(Map.of("data", body));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classification_code", outcome.classificationCode());
        data.put("classification_name", outcome.classificationName());
        data.put("status", String.valueOf(outcome.status()));
        // is_near_miss is the boolean the indicators count on, computed by the domain rather than by
        // each caller re-deriving it from a code prefix.
        data.put("is_near_miss", MaternalNearMissService.isNearMiss(outcome));
        // Provisional and the unresolved list are what stop a caller reading NOT_MET as "she was fine".
        // An INDETERMINATE outcome names the criteria still open, which is the recording work to do.
        data.put("provisional", outcome.provisional());
        data.put("unresolved_criteria", outcome.unresolvedTiers() == null
                ? List.of() : outcome.unresolvedTiers());
        data.put("missing_inputs", outcome.missingInputs() == null
                ? List.of() : outcome.missingInputs());
        data.put("rationale", outcome.rationale());
        data.put("review_required", MaternalNearMissService.isNearMiss(outcome));
        data.put("review_note", MaternalNearMissService.isNearMiss(outcome)
                ? "She met the WHO near-miss criteria and survived, so she can be asked what happened. "
                  + "Identification is not a review: MPDSR is the confidential no-blame instrument and "
                  + "is convened separately."
                : null);
        return ResponseEntity.ok(Map.of("data", data));
    }

    /** One woman's outcome in the reporting period. */
    public record OutcomeCaseRequest(String outcome) {
    }

    /**
     * The period's cases. {@code indicatorPeriod} is carried through untouched so a returned figure is
     * always attributable to the window it was computed over.
     */
    public record IndicatorRequest(String indicatorPeriod, List<OutcomeCaseRequest> cases) {
    }

    /**
     * Computes the near-miss-to-death ratio and the mortality index over a period's cases.
     *
     * <p>An unrecognised or absent outcome value is <b>rejected</b>, not silently bucketed. Defaulting
     * it to NOT_SEVERE would drop a woman out of the severe cohort — the precise failure the
     * indeterminate law exists to prevent — and defaulting it to indeterminate would invent a severe
     * outcome nobody recorded. Neither is a safe guess, so the caller is told which value was bad.
     */
    @PostMapping("/indicators")
    public ResponseEntity<Map<String, Object>> indicators(@RequestBody IndicatorRequest request) {
        List<IndicatorEngine.OutcomeCase> cases = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        if (request != null && request.cases() != null) {
            for (int i = 0; i < request.cases().size(); i++) {
                OutcomeCaseRequest c = request.cases().get(i);
                String raw = c == null ? null : c.outcome();
                IndicatorEngine.OutcomeCase parsed = parse(raw);
                if (parsed == null) {
                    rejected.add("case[" + i + "]: " + (raw == null ? "<absent>" : raw));
                } else {
                    cases.add(parsed);
                }
            }
        }

        if (!rejected.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "unclassifiable_outcome");
            body.put("message", "Every case must carry a recognised outcome. These did not, and were "
                    + "not guessed at, because bucketing an unknown outcome either drops a woman from "
                    + "the severe cohort or invents a severe outcome nobody recorded.");
            body.put("rejected_cases", rejected);
            body.put("accepted_values", List.of("NEAR_MISS", "MATERNAL_DEATH",
                    "NEAR_MISS_INDETERMINATE", "NOT_SEVERE"));
            return ResponseEntity.unprocessableEntity().body(Map.of("data", body));
        }

        IndicatorEngine.SevereOutcomeResult r = IndicatorEngine.computeSevereMaternalOutcomes(cases);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("indicator_period", request == null ? null : request.indicatorPeriod());
        data.put("near_miss_count", r.nearMiss());
        data.put("maternal_death_count", r.maternalDeaths());
        data.put("indeterminate_count", r.indeterminate());
        data.put("severe_maternal_outcome_count", r.severeMaternalOutcomes());
        // Nulls are sent as nulls. A ratio over zero deaths is undefined, and substituting 0 — or
        // worse, a serialised Infinity — produces a figure that gets ranked against other facilities.
        data.put("near_miss_to_death_ratio", r.nearMissToDeathRatio());
        data.put("near_miss_to_death_ratio_upper_bound", r.nearMissToDeathRatioUpperBound());
        data.put("mortality_index", r.mortalityIndex());
        data.put("mortality_index_lower_bound", r.mortalityIndexLowerBound());
        data.put("mortality_index_direction", "HIGHER_IS_WORSE");
        data.put("note", r.note());
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static IndicatorEngine.OutcomeCase parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return IndicatorEngine.OutcomeCase.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
