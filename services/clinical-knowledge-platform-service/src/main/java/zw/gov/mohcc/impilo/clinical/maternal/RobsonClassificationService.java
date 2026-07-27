package zw.gov.mohcc.impilo.clinical.maternal;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationTable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assigns a birth to its Robson group for caesarean-rate surveillance.
 *
 * <p>Reuses the IMNCI {@link ClassificationEngine} — the plan's "a ClassificationTable, not a new
 * engine". The ten groups are ordered so the overriding characteristics (multiple pregnancy,
 * abnormal lie, breech, preterm, previous caesarean) are tested before parity and onset, which is
 * what makes the classification mutually exclusive: a breech twin is group 8, not group 6.
 *
 * <p>The inputs are computed from the delivery record and the pregnancy episode and passed under the
 * {@code robson.*} prefix — the same injected-fact pattern as the self-monitored-BP reducer. Robson
 * asks a clinician nothing; it reads what the birth already recorded. A birth missing a defining
 * feature is INDETERMINATE, never forced into a group — a misclassified denominator is worse than an
 * uncounted one, because it silently moves a birth into the wrong group's rate.
 */
@Service
public class RobsonClassificationService {

    static final String CONTENT_PATH = "clinical/rmnp-robson-classification.json";

    private final ClassificationContentLoader contentLoader;

    public RobsonClassificationService(ClassificationContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    /**
     * The six characteristics the Robson classification needs, derived from the birth records.
     *
     * @param parity              NULLIPAROUS or MULTIPAROUS, from the derived gravidity/parity
     * @param previousCaesarean   YES or NO, from obstetric history
     * @param presentation        CEPHALIC, BREECH, or TRANSVERSE_OBLIQUE
     * @param gestationCategory   TERM (>=37 weeks) or PRETERM (<=36), from the stored gestational age
     * @param labourOnset         SPONTANEOUS, INDUCED, or PRELABOUR_CS, from the delivery record
     * @param multiplePregnancy   true for twins or more, from the fetus records / plurality
     */
    public record RobsonInputs(String parity, String previousCaesarean, String presentation,
                               String gestationCategory, String labourOnset,
                               Boolean multiplePregnancy) {
    }

    public ClassificationEngine.Outcome classify(RobsonInputs inputs) {
        return classify(CONTENT_PATH, inputs);
    }

    ClassificationEngine.Outcome classify(String resourcePath, RobsonInputs inputs) {
        ClassificationContentLoader.Pack pack = contentLoader.load(resourcePath);
        ClassificationTable table = pack == null ? null : pack.table("ROBSON_GROUP");
        if (table == null) {
            return null;
        }
        return ClassificationEngine.classify(table, null, toFacts(inputs));
    }

    private static Map<String, Object> toFacts(RobsonInputs in) {
        Map<String, Object> f = new LinkedHashMap<>();
        if (in == null) {
            return f;
        }
        // Only inject what is actually known. A null feature is left unset so the engine reports the
        // birth INDETERMINATE rather than defaulting it into a group.
        if (in.parity() != null) {
            f.put("robson.parity", in.parity());
        }
        if (in.previousCaesarean() != null) {
            f.put("robson.previousCaesarean", in.previousCaesarean());
        }
        if (in.presentation() != null) {
            f.put("robson.presentation", in.presentation());
        }
        if (in.gestationCategory() != null) {
            f.put("robson.gestationCategory", in.gestationCategory());
        }
        if (in.labourOnset() != null) {
            f.put("robson.labourOnset", in.labourOnset());
        }
        if (in.multiplePregnancy() != null) {
            f.put("robson.multiplePregnancy", in.multiplePregnancy());
        }
        return f;
    }
}
