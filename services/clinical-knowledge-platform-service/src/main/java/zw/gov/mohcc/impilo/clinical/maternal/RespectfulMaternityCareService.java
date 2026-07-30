package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the respectful-maternity-care measure set and normalises answers to it.
 *
 * <h2>An instrument, not an engine</h2>
 * <p>It defines what is asked and where each answer lands. It computes no verdict and drives no
 * clinical decision, so nothing here evaluates against the rule engine.
 *
 * <h2>Why the normalisation lives here and not in the caller</h2>
 * <p>Which items are reverse-scored is <b>ratifiable content</b>, not arithmetic. It changes when MOHCC
 * ratifies or revises the pack, and a copy of the list in the BFF or in Rito would mean a content
 * revision needed a service release — and, worse, that two callers could disagree about the polarity of
 * the same stored number. The service that owns the content owns the flip.
 *
 * <h2>What normalisation is for</h2>
 * <p>After it, <b>low always means worse care</b> wherever the score is read. Rito stores
 * {@code (domain, measure, score)} with no polarity column and its aggregator takes a plain mean, so a
 * negatively-worded item stored raw would pull a facility's mean in the wrong direction and be
 * invisible to every later reader. Nothing is lost by storing normalised: the flip is
 * {@code high + low - score}, its own inverse, so the woman's raw answer is exactly recoverable given
 * the instrument.
 *
 * <h2>A blank is not a middle answer</h2>
 * <p>An unanswered item is excluded entirely and reported as such. Scoring it 3 of 5 would turn a woman
 * who was never asked into a woman who was ambivalent, and would let a facility improve its mean by
 * asking fewer questions.
 */
@Service
public class RespectfulMaternityCareService {

    private static final Logger log = LoggerFactory.getLogger(RespectfulMaternityCareService.class);

    static final String CONTENT_PATH = "clinical/rmnp-respectful-maternity-care.json";

    private final ObjectMapper objectMapper;
    private volatile Instrument cached;

    public RespectfulMaternityCareService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** One measure as the instrument defines it. */
    public record Measure(String measure, String prompt, String mistreatmentCategory,
                          boolean reverseScored, String note) {
    }

    public record Scale(String type, int low, int high, String lowMeans, String highMeans, String note) {
    }

    public record Instrument(String instrumentId, String name, String appliesTo, String ratingDomain,
                             String approvalStatus, String adaptationAuthority,
                             Scale scale, List<Measure> measures, List<String> reportingRules,
                             String anonymousByDefault, String regulationFirewall) {

        public Measure measure(String code) {
            if (code == null) {
                return null;
            }
            for (Measure m : measures) {
                if (m.measure().equalsIgnoreCase(code.trim())) {
                    return m;
                }
            }
            return null;
        }
    }

    public Instrument instrument() {
        Instrument local = cached;
        if (local == null) {
            local = read(CONTENT_PATH);
            cached = local;
        }
        return local;
    }

    /**
     * One normalised score, ready to be written to Rito's existing
     * {@code rit_rating_domain_score (domain, measure, score, scale)}.
     *
     * @param normalisedScore the value to store, always with low meaning worse care
     * @param rawScore        what the woman actually answered, carried so the transformation is
     *                        auditable rather than something a reader has to trust
     */
    public record NormalisedScore(String domain, String measure, BigDecimal normalisedScore,
                                  BigDecimal rawScore, boolean reverseScored, String scale,
                                  String mistreatmentCategory) {
    }

    /**
     * @param scores       the answers that can be stored
     * @param notAsked     measures the instrument defines that this submission did not answer, named so
     *                     a low-coverage submission reports low confidence rather than a good score
     * @param unknownCodes answered codes the instrument does not define — refused, never stored under a
     *                     measure name the aggregator cannot interpret
     */
    public record Normalisation(List<NormalisedScore> scores, List<String> notAsked,
                                List<String> unknownCodes, String note) {
    }

    /**
     * Normalises a set of raw answers keyed by measure code.
     *
     * <p>A null or out-of-range answer is treated as not asked rather than clamped. Clamping a 7 to 5
     * on a five-point scale invents an answer, and on this instrument the invented answer would be the
     * most favourable one available.
     */
    public Normalisation normalise(Map<String, ? extends Number> answers) {
        Instrument in = instrument();
        if (in == null) {
            return null;
        }

        List<NormalisedScore> scores = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> notAsked = new ArrayList<>();
        Map<String, Number> given = answers == null ? Map.of() : new LinkedHashMap<>(answers);

        for (Map.Entry<String, Number> e : given.entrySet()) {
            Measure m = in.measure(e.getKey());
            if (m == null) {
                unknown.add(e.getKey());
            }
        }

        for (Measure m : in.measures()) {
            Number raw = null;
            for (Map.Entry<String, Number> e : given.entrySet()) {
                if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(m.measure())) {
                    raw = e.getValue();
                    break;
                }
            }
            if (raw == null) {
                notAsked.add(m.measure());
                continue;
            }
            BigDecimal rawScore = new BigDecimal(raw.toString());
            if (rawScore.compareTo(BigDecimal.valueOf(in.scale().low())) < 0
                    || rawScore.compareTo(BigDecimal.valueOf(in.scale().high())) > 0) {
                // Out of range is not an answer. Clamping would fabricate one, and on a scale where
                // high means better care the clamp would fabricate the flattering end.
                notAsked.add(m.measure());
                unknown.add(m.measure() + "=" + rawScore.toPlainString() + " (outside "
                        + in.scale().low() + "-" + in.scale().high() + ")");
                continue;
            }
            BigDecimal normalised = m.reverseScored()
                    ? BigDecimal.valueOf(in.scale().high() + in.scale().low()).subtract(rawScore)
                    : rawScore;
            scores.add(new NormalisedScore(in.ratingDomain(), m.measure(),
                    normalised.setScale(2, RoundingMode.UNNECESSARY),
                    rawScore.setScale(2, RoundingMode.UNNECESSARY),
                    m.reverseScored(),
                    in.scale().low() + "-" + in.scale().high(),
                    m.mistreatmentCategory()));
        }

        return new Normalisation(List.copyOf(scores), List.copyOf(notAsked), List.copyOf(unknown),
                note(scores.size(), in.measures().size(), notAsked.size()));
    }

    private static String note(int answered, int total, int notAsked) {
        if (answered == 0) {
            return "No measure was answered, so there is nothing to store. This is an absence of "
                    + "evidence, not evidence of respectful care.";
        }
        String base = answered + " of " + total + " measures answered.";
        if (notAsked > 0) {
            return base + " The remaining " + notAsked + " are excluded rather than imputed, so read "
                    + "this as low coverage rather than as a score. A facility does not improve by "
                    + "asking fewer questions.";
        }
        return base + " Reverse-scored items are stored already normalised, so low means worse care "
                + "wherever this is read.";
    }

    Instrument read(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Respectful maternity care measure set not found on the classpath: {}",
                        resourcePath);
                return null;
            }
            JsonNode root = objectMapper.readTree(stream);
            JsonNode instrument = root.path("instrument");
            JsonNode scaleNode = instrument.path("scale");

            List<Measure> measures = new ArrayList<>();
            for (JsonNode node : instrument.path("measures")) {
                measures.add(new Measure(
                        node.path("measure").asText(),
                        node.path("prompt").asText(),
                        node.path("mistreatmentCategory").asText(null),
                        // Absent means not reverse-scored. That is the safe default: wrongly reversing
                        // a positively-worded item reports a facility with no abuse as the worst one.
                        node.path("reverseScored").asBoolean(false),
                        node.path("note").asText(null)));
            }

            List<String> rules = new ArrayList<>();
            for (JsonNode node : instrument.path("reportingRules")) {
                rules.add(node.asText());
            }

            return new Instrument(
                    instrument.path("instrumentId").asText(),
                    instrument.path("name").asText(),
                    instrument.path("appliesTo").asText(null),
                    instrument.path("ratingDomain").asText(),
                    // Carried so no consumer can mistake an engineering seed for ratified national
                    // policy, the same reason the policy-parameter surface carries its statuses.
                    root.path("approvalStatus").asText(null),
                    root.path("adaptationAuthority").asText(null),
                    new Scale(
                            scaleNode.path("type").asText("LIKERT_5"),
                            scaleNode.path("low").asInt(1),
                            scaleNode.path("high").asInt(5),
                            scaleNode.path("lowMeans").asText(null),
                            scaleNode.path("highMeans").asText(null),
                            scaleNode.path("note").asText(null)),
                    List.copyOf(measures),
                    List.copyOf(rules),
                    root.path("provenance").path("anonymousByDefault").asText(null),
                    root.path("provenance").path("regulationFirewall").asText(null));
        } catch (IOException e) {
            log.error("Failed to read the respectful maternity care measure set: {}", e.getMessage());
            return null;
        }
    }
}
