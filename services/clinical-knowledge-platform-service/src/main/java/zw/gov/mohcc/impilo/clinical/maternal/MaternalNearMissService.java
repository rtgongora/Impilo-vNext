package zw.gov.mohcc.impilo.clinical.maternal;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationTable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identifies a maternal near-miss against the WHO organ-dysfunction criteria (WHO near-miss approach,
 * 2011).
 *
 * <p>Reuses the shared {@link ClassificationEngine} rather than introducing an engine — the same
 * decision as Robson. A near-miss is a classification over recorded facts, not a new kind of
 * reasoning.
 *
 * <h2>Identification is not review</h2>
 * <p>This answers "did this woman meet the criteria?". It does not conduct, schedule or store a
 * review. MPDSR — the confidential, no-blame review instrument — is specified separately and
 * deliberately not built here, because a review is a governance instrument with its own
 * confidentiality treatment and its own firewall against the facility register.
 *
 * <h2>Near-miss identification carries no confidentiality stamp</h2>
 * <p>Deliberately. It is ordinary clinical classification. Putting a survival statistic behind a
 * confidentiality grant would make the near-miss-to-death ratio uncomputable for the people who need
 * it, and would confuse the protection that MPDSR genuinely needs with one that this does not.
 *
 * <h2>Silence is not a negative</h2>
 * <p>Facts are injected only when known. An unrecorded observation leaves its criterion unresolved,
 * so the engine reports INDETERMINATE rather than reaching the not-met row. "We did not measure her
 * creatinine" must never read as "her creatinine was normal".
 */
@Service
public class MaternalNearMissService {

    static final String CONTENT_PATH = "clinical/rmnp-maternal-near-miss.json";
    static final String TABLE_ID = "MATERNAL_NEAR_MISS";

    /** The classification reached when no criterion fired and none was unresolved. */
    public static final String NOT_MET = "NEAR_MISS_NOT_MET";

    private final ClassificationContentLoader contentLoader;

    public MaternalNearMissService(ClassificationContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    /**
     * Organ-dysfunction observations. Every field is nullable and a null means NOT RECORDED, never
     * "absent" — the distinction the WHO approach depends on, since a near-miss missed for want of a
     * recorded observation is a near-miss counted as a normal birth.
     */
    public record NearMissObservations(
            // Cardiovascular
            Boolean shock, Boolean cardiacArrest, Boolean continuousVasoactiveDrugs,
            Boolean cardiopulmonaryResuscitation, Double lactateMmolL, Double phArterial,
            // Respiratory
            Boolean acuteCyanosis, Boolean gasping, Integer respiratoryRate,
            Integer intubationNotForAnaesthesiaMinutes, Boolean oxygenSaturationBelow90ForOverAnHour,
            // Renal
            Boolean oliguriaNonResponsiveToFluids, Integer creatinineUmolL, Boolean dialysisForAcuteRenalFailure,
            // Coagulation / haematological
            Boolean clottingFailure, Integer plateletsPerUl, Integer unitsTransfused,
            // Hepatic
            Boolean jaundiceWithPreEclampsia, Integer bilirubinUmolL,
            // Neurological
            Boolean prolongedUnconsciousness, Boolean stroke, Boolean uncontrollableFits, Boolean totalParalysis,
            // Uterine
            Boolean hysterectomyForInfectionOrHaemorrhage) {
    }

    public ClassificationEngine.Outcome classify(NearMissObservations observations) {
        return classify(CONTENT_PATH, observations);
    }

    ClassificationEngine.Outcome classify(String resourcePath, NearMissObservations observations) {
        ClassificationContentLoader.Pack pack = contentLoader.load(resourcePath);
        ClassificationTable table = pack == null ? null : pack.table(TABLE_ID);
        if (table == null) {
            return null;
        }
        return ClassificationEngine.classify(table, null, toFacts(observations));
    }

    /** Whether an outcome represents a confirmed near-miss (any organ system), for the indicators. */
    public static boolean isNearMiss(ClassificationEngine.Outcome outcome) {
        return outcome != null
                && outcome.classificationCode() != null
                && outcome.classificationCode().startsWith("NEAR_MISS_")
                && !NOT_MET.equals(outcome.classificationCode());
    }

    private static Map<String, Object> toFacts(NearMissObservations o) {
        Map<String, Object> f = new LinkedHashMap<>();
        if (o == null) {
            return f;
        }
        put(f, "nearMiss.shock", o.shock());
        put(f, "nearMiss.cardiacArrest", o.cardiacArrest());
        put(f, "nearMiss.continuousVasoactiveDrugs", o.continuousVasoactiveDrugs());
        put(f, "nearMiss.cardiopulmonaryResuscitation", o.cardiopulmonaryResuscitation());
        put(f, "nearMiss.lactateMmolL", o.lactateMmolL());
        put(f, "nearMiss.phArterial", o.phArterial());
        put(f, "nearMiss.acuteCyanosis", o.acuteCyanosis());
        put(f, "nearMiss.gasping", o.gasping());
        put(f, "nearMiss.respiratoryRate", o.respiratoryRate());
        put(f, "nearMiss.intubationNotForAnaesthesiaMinutes", o.intubationNotForAnaesthesiaMinutes());
        put(f, "nearMiss.oxygenSaturationBelow90ForOverAnHour", o.oxygenSaturationBelow90ForOverAnHour());
        put(f, "nearMiss.oliguriaNonResponsiveToFluids", o.oliguriaNonResponsiveToFluids());
        put(f, "nearMiss.creatinineUmolL", o.creatinineUmolL());
        put(f, "nearMiss.dialysisForAcuteRenalFailure", o.dialysisForAcuteRenalFailure());
        put(f, "nearMiss.clottingFailure", o.clottingFailure());
        put(f, "nearMiss.plateletsPerUl", o.plateletsPerUl());
        put(f, "nearMiss.unitsTransfused", o.unitsTransfused());
        put(f, "nearMiss.jaundiceWithPreEclampsia", o.jaundiceWithPreEclampsia());
        put(f, "nearMiss.bilirubinUmolL", o.bilirubinUmolL());
        put(f, "nearMiss.prolongedUnconsciousness", o.prolongedUnconsciousness());
        put(f, "nearMiss.stroke", o.stroke());
        put(f, "nearMiss.uncontrollableFits", o.uncontrollableFits());
        put(f, "nearMiss.totalParalysis", o.totalParalysis());
        put(f, "nearMiss.hysterectomyForInfectionOrHaemorrhage", o.hysterectomyForInfectionOrHaemorrhage());
        return f;
    }

    /** Only known facts are injected — an unset key is unresolved, not false. */
    private static void put(Map<String, Object> facts, String key, Object value) {
        if (value != null) {
            facts.put(key, value);
        }
    }
}
