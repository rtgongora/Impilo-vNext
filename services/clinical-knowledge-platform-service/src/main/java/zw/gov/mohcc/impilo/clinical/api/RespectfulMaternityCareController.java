package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.maternal.RespectfulMaternityCareService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The respectful-maternity-care instrument: its measure definitions, and the normalisation that makes
 * its answers safe to store.
 *
 * <h2>Definitions, not a verdict</h2>
 * <p>This serves what to ask and how to score it. It reaches no conclusion about a provider or a
 * facility, and it is not a rule pack.
 *
 * <h2>The regulation firewall is carried, not assumed</h2>
 * <p>Both responses carry Rito's inherited rule verbatim: a rating never mutates a licence, scope,
 * employment or access. A consumer that renders these measures next to a provider's registration is
 * building the thing this instrument must not become, so the constraint travels with the payload rather
 * than living only in a document.
 */
@RestController
@RequestMapping("/internal/v1/clinical/maternal/respectful-care")
public class RespectfulMaternityCareController {

    private final RespectfulMaternityCareService service;

    public RespectfulMaternityCareController(RespectfulMaternityCareService service) {
        this.service = service;
    }

    /** The measure set a client renders: prompts, scale, and which items are reverse-scored. */
    @GetMapping("/instrument")
    public ResponseEntity<Map<String, Object>> instrument() {
        RespectfulMaternityCareService.Instrument in = service.instrument();
        if (in == null) {
            return ResponseEntity.status(502).body(Map.of("data", Map.of(
                    "error", "instrument_unavailable",
                    "message", "The respectful maternity care measure set could not be loaded. Do not "
                            + "substitute a local copy of the questions: the polarity of a "
                            + "reverse-scored item is content, and a stale copy stores it backwards.")));
        }

        List<Map<String, Object>> measures = new ArrayList<>();
        for (RespectfulMaternityCareService.Measure m : in.measures()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("measure", m.measure());
            row.put("prompt", m.prompt());
            row.put("mistreatment_category", m.mistreatmentCategory());
            // Exposed so a renderer can show the negatively-worded items in their own voice, and so a
            // client can verify the normalisation it received rather than trusting it.
            row.put("reverse_scored", m.reverseScored());
            row.put("note", m.note());
            measures.add(row);
        }

        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("type", in.scale().type());
        scale.put("low", in.scale().low());
        scale.put("high", in.scale().high());
        scale.put("low_means", in.scale().lowMeans());
        scale.put("high_means", in.scale().highMeans());
        scale.put("note", in.scale().note());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instrument_id", in.instrumentId());
        data.put("name", in.name());
        data.put("applies_to", in.appliesTo());
        data.put("rating_domain", in.ratingDomain());
        // An unratified seed must never be presented as national policy, the same reason the
        // policy-parameter surface always carries its approval status beside the value.
        data.put("approval_status", in.approvalStatus());
        data.put("adaptation_authority", in.adaptationAuthority());
        data.put("scale", scale);
        data.put("measures", measures);
        data.put("reporting_rules", in.reportingRules());
        data.put("anonymous_by_default", in.anonymousByDefault());
        data.put("regulation_firewall", in.regulationFirewall());
        return ResponseEntity.ok(Map.of("data", data));
    }

    /** Raw answers keyed by measure code, as the woman gave them. */
    public record NormaliseRequest(Map<String, BigDecimal> answers) {
    }

    /**
     * Turns raw answers into Rito-shaped domain scores with the reverse-scored items already flipped.
     *
     * <p>Callers submit the returned {@code scores} to Rito's existing rating lane unchanged. The point
     * is that no caller decides polarity: the content owns it, so a revision to the instrument cannot
     * leave one client storing a measure backwards while another stores it forwards.
     */
    @PostMapping("/normalise")
    public ResponseEntity<Map<String, Object>> normalise(
            @RequestBody(required = false) NormaliseRequest request) {

        RespectfulMaternityCareService.Normalisation n = service.normalise(
                request == null ? Map.of() : request.answers());
        if (n == null) {
            return ResponseEntity.status(502).body(Map.of("data", Map.of(
                    "error", "instrument_unavailable",
                    "message", "The measure set could not be loaded, so answers were not normalised. "
                            + "Storing them un-normalised would record a negatively-worded item "
                            + "backwards and pull the facility's mean the wrong way.")));
        }

        List<Map<String, Object>> scores = new ArrayList<>();
        for (RespectfulMaternityCareService.NormalisedScore s : n.scores()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("domain", s.domain());
            row.put("measure", s.measure());
            row.put("score", s.normalisedScore());
            row.put("scale", s.scale());
            // Both values travel. Storing only the normalised number would make the transformation
            // something a reader has to trust rather than something they can check.
            row.put("raw_score", s.rawScore());
            row.put("reverse_scored", s.reverseScored());
            row.put("mistreatment_category", s.mistreatmentCategory());
            scores.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scores", scores);
        // Named, not counted: which questions went unasked is the coverage story, and a bare count
        // cannot tell a programme that nobody asks about detention.
        data.put("not_asked", n.notAsked());
        data.put("unknown_codes", n.unknownCodes());
        data.put("answered_count", scores.size());
        data.put("note", n.note());
        return ResponseEntity.ok(Map.of("data", data));
    }
}
