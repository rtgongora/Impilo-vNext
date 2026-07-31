package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.maternal.HomeBpSeriesReducer;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalDangerSignService;
import zw.gov.mohcc.impilo.clinical.maternal.SmbpEscalationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The self-measured blood-pressure series verdict, over HTTP.
 *
 * <h2>It consumes readings; it does not store them</h2>
 * <p>Telemonitoring owns {@code tm_readings} and is the single SHR writer of monitoring-band
 * Observations. This endpoint takes a series it is handed and returns a clinical reading of it. A
 * second reading store would guarantee two answers to "what was her blood pressure last Tuesday",
 * and the disagreement would surface between a citizen screen and a clinician's chart. So this is a
 * POST that persists nothing — an assessment, not an intake.
 *
 * <h2>The two verdicts that are not alerts are the ones that go wrong</h2>
 * <p>{@code INSUFFICIENT_DATA} must never render as reassurance and {@code CONTROLLED} must never be
 * reached without enough readings. The engine already refuses to conflate them; this surface carries
 * the whole summary — reading count, mean count, the worst single reading, sufficiency — so a client
 * cannot construct a green state from a verdict it did not fully read.
 *
 * <h2>A danger sign that was never asked about is not one that was denied</h2>
 * <p>{@code dangerSignPresent} is boxed and forwarded as-is. Null leaves the fact unset so the rule
 * reads UNKNOWN; coercing it to false here would assert on the woman's behalf that she has no danger
 * signs, which is the single most dangerous default available on this pathway.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/smbp")
public class SmbpEscalationController {

    private final SmbpEscalationService escalation;

    public SmbpEscalationController(SmbpEscalationService escalation) {
        this.escalation = escalation;
    }

    /**
     * One home reading as the client sends it.
     *
     * <p>{@code firstOfSession} defaults to false when absent, which is the safe direction: a reading
     * wrongly counted toward the mean dilutes it slightly, whereas a reading wrongly discarded is one
     * fewer toward sufficiency and could hold a woman at INSUFFICIENT_DATA. The severe check sees
     * every reading either way.
     */
    public record ReadingRequest(Integer systolic, Integer diastolic, Boolean firstOfSession) {
    }

    /** Home-BP thresholds, all optional; absent means the governed seed. */
    public record ThresholdsRequest(
            Integer homeHypertensionSystolic,
            Integer homeHypertensionDiastolic,
            Integer severeSystolic,
            Integer severeDiastolic,
            Integer minimumReadingsToConclude) {
    }

    public record AssessRequest(
            List<ReadingRequest> readings,
            Boolean dangerSignPresent,
            ThresholdsRequest thresholds) {
    }

    @PostMapping("/assess")
    public ResponseEntity<Map<String, Object>> assess(@RequestBody AssessRequest request) {
        SmbpEscalationService.Result result = escalation.assess(
                readings(request.readings()),
                request.dangerSignPresent(),
                thresholds(request.thresholds()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verdict", result.verdict().name());
        data.put("message", result.message());
        data.put("note", result.note());
        data.put("summary", summary(result.summary()));
        data.put("alerts", result.alerts().stream().map(SmbpEscalationController::alert).toList());
        // Carried beside the verdict for the same reason PolicyParameterController carries them: a
        // consumer reading only the verdict would treat an unratified engineering seed as national
        // clinical policy.
        data.put("contentVersion", result.contentVersion());
        data.put("approvalStatus", result.approvalStatus());
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static List<HomeBpSeriesReducer.Reading> readings(List<ReadingRequest> requested) {
        List<HomeBpSeriesReducer.Reading> out = new ArrayList<>();
        if (requested == null) {
            return out;
        }
        for (ReadingRequest r : requested) {
            // A reading missing either value is not half a reading. Dropped rather than zero-filled:
            // a systolic of 0 would sit in the mean as the calmest reading of the week.
            if (r == null || r.systolic() == null || r.diastolic() == null) {
                continue;
            }
            out.add(new HomeBpSeriesReducer.Reading(
                    r.systolic(), r.diastolic(), Boolean.TRUE.equals(r.firstOfSession())));
        }
        return out;
    }

    /**
     * Thresholds, with each absent value falling back to its own seed rather than the whole record
     * falling back. A client that overrides only the minimum-readings count must not silently also
     * reset the severe cut-offs to whatever this version happens to seed.
     */
    private static HomeBpSeriesReducer.Thresholds thresholds(ThresholdsRequest t) {
        if (t == null) {
            return null;
        }
        HomeBpSeriesReducer.Thresholds seed = HomeBpSeriesReducer.Thresholds.seed();
        return new HomeBpSeriesReducer.Thresholds(
                t.homeHypertensionSystolic() == null
                        ? seed.homeHypertensionSystolic() : t.homeHypertensionSystolic(),
                t.homeHypertensionDiastolic() == null
                        ? seed.homeHypertensionDiastolic() : t.homeHypertensionDiastolic(),
                t.severeSystolic() == null ? seed.severeSystolic() : t.severeSystolic(),
                t.severeDiastolic() == null ? seed.severeDiastolic() : t.severeDiastolic(),
                t.minimumReadingsToConclude() == null
                        ? seed.minimumReadingsToConclude() : t.minimumReadingsToConclude());
    }

    private static Map<String, Object> summary(HomeBpSeriesReducer.Summary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("readingCount", s.readingCount());
        m.put("meanCount", s.meanCount());
        // Null when nothing counted toward the mean. Reported as null rather than 0, because a mean of
        // 0/0 is the most reassuring blood pressure ever recorded.
        m.put("meanSystolic", s.meanSystolic());
        m.put("meanDiastolic", s.meanDiastolic());
        m.put("maxSystolic", s.maxSystolic());
        m.put("maxDiastolic", s.maxDiastolic());
        m.put("anySevere", s.anySevere());
        m.put("elevatedMean", s.elevatedMean());
        m.put("sufficient", s.sufficient());
        return m;
    }

    private static Map<String, Object> alert(MaternalDangerSignService.Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.code());
        m.put("name", a.name());
        m.put("severity", a.severity());
        m.put("interruptive", a.interruptive());
        m.put("referralRequired", a.referralRequired());
        m.put("message", a.message());
        m.put("explanation", a.explanation());
        // The action, not just the finding. An alert that says what is wrong without saying what to do
        // is a notification, and the woman reading it on a phone at home has nobody to interpret it.
        m.put("requiredAction", a.requiredAction());
        m.put("monitoringInstruction", a.monitoringInstruction());
        m.put("triggeringFindings", a.triggeringFindings());
        m.put("sourceRefs", a.sourceRefs());
        return m;
    }
}
