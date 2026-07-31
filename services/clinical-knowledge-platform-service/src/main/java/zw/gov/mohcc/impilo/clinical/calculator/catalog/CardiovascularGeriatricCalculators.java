package zw.gov.mohcc.impilo.clinical.calculator.catalog;

import zw.gov.mohcc.impilo.clinical.calculator.CalculatorDescriptor;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorInput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOption;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOutput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorRegistration;
import zw.gov.mohcc.impilo.medicine.cardio.Cha2ds2VascScore;
import zw.gov.mohcc.impilo.medicine.cardio.CvdRiskCalculator;
import zw.gov.mohcc.impilo.medicine.cardio.NyhaClass;
import zw.gov.mohcc.impilo.medicine.cognition.CognitiveScreenScore;
import zw.gov.mohcc.impilo.medicine.common.Sex;
import zw.gov.mohcc.impilo.medicine.frailty.ClinicalFrailtyScale;
import zw.gov.mohcc.impilo.medicine.pharm.OpioidEquianalgesic;

import java.util.ArrayList;
import java.util.List;

/**
 * Cardiovascular, geriatric and pharmacological instruments.
 *
 * <p>Three of these carry a caveat that is not about the patient but about this system: the
 * cardiovascular risk model is an engineering seed rather than a WHO chart reading, the Clinical
 * Frailty Scale's official descriptors are licensed and not reproduced, and the cognitive-screen
 * bands are arithmetic fifths rather than impairment categories. Each states that on the form, not
 * only in a footnote, because a surface that renders the number without the caveat is
 * misrepresenting it.</p>
 */
public final class CardiovascularGeriatricCalculators {

    private CardiovascularGeriatricCalculators() {
    }

    /** Every calculator this catalogue contributes. */
    public static List<CalculatorRegistration> registrations() {
        List<CalculatorRegistration> all = new ArrayList<>();
        all.add(cha2ds2Vasc());
        all.add(nyha());
        all.add(cvdRiskLaboratory());
        all.add(cvdRiskNonLaboratory());
        all.add(clinicalFrailtyScale());
        all.add(cognitiveScreen());
        all.add(opioidEquivalent());
        return List.copyOf(all);
    }

    private static final List<CalculatorOption> SEX_OPTIONS =
            EnumOptions.of(Sex.class, sex -> sex == Sex.FEMALE ? "Female" : "Male");

    // --- cardiovascular --------------------------------------------------------------------------

    private static CalculatorRegistration cha2ds2Vasc() {
        var descriptor = new CalculatorDescriptor(
                "cha2ds2-vasc",
                "CHA₂DS₂-VASc",
                "Cardiovascular",
                "Stroke risk in non-valvular atrial fibrillation.",
                "Lip GYH, Nieuwlaat R, Pisters R, Lane DA, Crijns HJGM. Chest 2010;137:263–272."
                        + " Primary text not vendored in this system.",
                List.of(
                        CalculatorInput.integer("age_years", "Age", "years",
                                "Scored in three bands: under 65, 65 to 74, and 75 or over."),
                        CalculatorInput.choice("sex", "Sex", SEX_OPTIONS,
                                "Female sex scores a point, but see the caveat: it is a risk modifier"
                                        + " rather than a risk factor."),
                        CalculatorInput.multiChoice("criteria", "Risk factors present",
                                EnumOptions.of(Cha2ds2VascScore.Criterion.class,
                                        Cha2ds2VascScore.Criterion::label),
                                "Select every factor present. Selecting none is a valid answer meaning"
                                        + " the history was taken and nothing was found.")),
                List.of(
                        "Female sex alone does not justify anticoagulation. A woman scoring 1 from sex"
                                + " alone carries the risk of a man scoring 0, which is why the score"
                                + " excluding sex is reported alongside the total.",
                        "The score counts risk factors. It does not weigh bleeding risk, select an"
                                + " anticoagulant, or decide whether to start one.",
                        "It is not valid in valvular atrial fibrillation or with a mechanical valve,"
                                + " where anticoagulation is indicated regardless of score."),
                Cha2ds2VascScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = Cha2ds2VascScore.of(
                    in.integer("age_years"),
                    in.enumValue("sex", Sex.class),
                    in.enumSet("criteria", Cha2ds2VascScore.Criterion.class));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("score", "CHA₂DS₂-VASc", result.score(), null),
                    CalculatorOutput.primary("score_excluding_sex", "Score excluding the sex point",
                            result.scoreExcludingSex(), null,
                            "Compare against this when the only point is female sex."),
                    CalculatorOutput.secondary("age_points", "Age points", result.agePoints(), null),
                    CalculatorOutput.secondary("sex_points", "Sex points", result.sexPoints(), null),
                    CalculatorOutput.secondary("criteria_met", "Risk factors that scored",
                            result.criteriaMet().stream().map(Enum::name).toList(), null)));
        });
    }

    private static CalculatorRegistration nyha() {
        var descriptor = new CalculatorDescriptor(
                "nyha-class",
                "NYHA functional class",
                "Cardiovascular",
                "The New York Heart Association functional classification, I to IV, from what the"
                        + " person can do.",
                "The Criteria Committee of the New York Heart Association, Nomenclature and Criteria"
                        + " for Diagnosis of Diseases of the Heart and Great Vessels, 9th edition,"
                        + " 1994. Primary text not vendored in this system.",
                List.of(
                        CalculatorInput.optionalBool("symptoms_at_rest",
                                "Symptoms at rest?", null),
                        CalculatorInput.optionalBool("symptoms_on_less_than_ordinary_activity",
                                "Symptoms on less than ordinary activity?", null),
                        CalculatorInput.optionalBool("symptoms_on_ordinary_activity",
                                "Symptoms on ordinary activity?",
                                "Leave a question unanswered rather than guessing. An unasked question"
                                        + " read as a 'no' walks a class IV patient down to class I.")),
                List.of(
                        "The classification describes what a person can do. Which class starts a"
                                + " medicine, warrants a device or triggers referral is policy, not"
                                + " arithmetic.",
                        "The classification is observer-dependent: in published studies two clinicians"
                                + " assessing the same patient agree only about half the time. A"
                                + " recorded class should carry who assessed it."),
                NyhaClass.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = NyhaClass.classify(
                    in.bool("symptoms_at_rest"),
                    in.bool("symptoms_on_less_than_ordinary_activity"),
                    in.bool("symptoms_on_ordinary_activity"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("class", "NYHA class",
                            result.nyhaClass().romanNumeral(), null),
                    CalculatorOutput.secondary("numeral", "Ordinal severity",
                            result.nyhaClass().numeral(), null),
                    CalculatorOutput.secondary("criteria", "Published criteria for this class",
                            result.nyhaClass().criteria(), null)));
        });
    }

    private static final List<String> CVD_CAVEATS = List.of(
            "This band comes from an Impilo engineering model, not from a WHO chart. The WHO AFR-E"
                    + " chart cell values are not held in this system, so the model reproduces the"
                    + " published variables and direction but not the calibration. Treat it as a"
                    + " prompt to check the printed chart, not a substitute for it.",
            "No percentage is produced, deliberately. A figure such as '23%' would carry a"
                    + " precision this model has not earned and would be indistinguishable from a"
                    + " real chart reading once stored.",
            "The charts estimate the risk of a first cardiovascular event and do not apply to"
                    + " someone with established cardiovascular disease.");

    private static CalculatorRegistration cvdRiskLaboratory() {
        var descriptor = new CalculatorDescriptor(
                "cvd-risk-laboratory",
                "Cardiovascular risk (laboratory)",
                "Cardiovascular",
                "Ten-year cardiovascular risk band using cholesterol and diabetes status.",
                "Model structure from WHO CVD Risk Chart Working Group, Lancet Glob Health"
                        + " 2019;7:e1332–45, and WHO HEARTS. Chart cells not vendored; the combination"
                        + " is an Impilo engineering seed.",
                List.of(
                        CalculatorInput.integer("age_years", "Age", "years",
                                "The charts publish rows for ages 40 to 74 only."),
                        CalculatorInput.choice("sex", "Sex", SEX_OPTIONS, null),
                        CalculatorInput.bool("current_smoker", "Current smoker?",
                                "Must be answered. Assuming non-smoking moves most people down a"
                                        + " band."),
                        CalculatorInput.bool("diabetes", "Diabetes?", null),
                        CalculatorInput.decimal("systolic_mmhg", "Systolic blood pressure", "mmHg",
                                null),
                        CalculatorInput.decimal("total_cholesterol_mmol_l", "Total cholesterol",
                                "mmol/L", "Total cholesterol, not LDL."),
                        CalculatorInput.bool("established_cvd",
                                "Established cardiovascular disease?",
                                "If yes, the calculation is refused: the charts estimate a first"
                                        + " event only.")),
                CVD_CAVEATS,
                CvdRiskCalculator.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = CvdRiskCalculator.laboratory(
                    in.integer("age_years"),
                    in.enumValue("sex", Sex.class),
                    in.bool("current_smoker"),
                    in.bool("diabetes"),
                    in.decimal("systolic_mmhg"),
                    in.decimal("total_cholesterol_mmol_l"),
                    in.bool("established_cvd"));
            return cvdResult(descriptor, result);
        });
    }

    private static CalculatorRegistration cvdRiskNonLaboratory() {
        var descriptor = new CalculatorDescriptor(
                "cvd-risk-non-laboratory",
                "Cardiovascular risk (non-laboratory)",
                "Cardiovascular",
                "Ten-year cardiovascular risk band using body mass index in place of cholesterol, for"
                        + " a clinic with a scale, a tape measure and a cuff.",
                "Model structure from WHO CVD Risk Chart Working Group, Lancet Glob Health"
                        + " 2019;7:e1332–45. Chart cells not vendored; the combination is an Impilo"
                        + " engineering seed.",
                List.of(
                        CalculatorInput.integer("age_years", "Age", "years",
                                "The charts publish rows for ages 40 to 74 only."),
                        CalculatorInput.choice("sex", "Sex", SEX_OPTIONS, null),
                        CalculatorInput.bool("current_smoker", "Current smoker?", null),
                        CalculatorInput.decimal("systolic_mmhg", "Systolic blood pressure", "mmHg",
                                null),
                        CalculatorInput.decimal("bmi_kg_m2", "Body mass index", "kg/m²", null),
                        CalculatorInput.bool("established_cvd",
                                "Established cardiovascular disease?", null)),
                List.of(
                        CVD_CAVEATS.get(0),
                        CVD_CAVEATS.get(1),
                        CVD_CAVEATS.get(2),
                        "This variant deliberately has no diabetes term, following the published"
                                + " chart: diagnosing diabetes needs the laboratory the variant exists"
                                + " to do without. It is its own instrument, not a degraded version of"
                                + " the laboratory chart."),
                CvdRiskCalculator.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = CvdRiskCalculator.nonLaboratory(
                    in.integer("age_years"),
                    in.enumValue("sex", Sex.class),
                    in.bool("current_smoker"),
                    in.decimal("systolic_mmhg"),
                    in.decimal("bmi_kg_m2"),
                    in.bool("established_cvd"));
            return cvdResult(descriptor, result);
        });
    }

    private static zw.gov.mohcc.impilo.clinical.calculator.CalculatorResult cvdResult(
            CalculatorDescriptor descriptor, CvdRiskCalculator.Result result) {
        if (!result.computed()) {
            return descriptor.refuse(result.reason().name(), result.explanation());
        }
        return descriptor.produce(result.contentVersion(), List.of(
                CalculatorOutput.primary("band", "Ten-year risk band", result.band().name(), null,
                        result.requiresSeedCaveat() ? result.caveat() : null),
                CalculatorOutput.secondary("variant", "Chart variant", result.variant().name(), null),
                CalculatorOutput.secondary("who_region", "WHO region", result.whoRegion(), null)));
    }

    // --- geriatrics ------------------------------------------------------------------------------

    private static CalculatorRegistration clinicalFrailtyScale() {
        var descriptor = new CalculatorDescriptor(
                "clinical-frailty-scale",
                "Clinical Frailty Scale",
                "Geriatrics",
                "Names the Rockwood Clinical Frailty Scale level for a score a clinician has"
                        + " assigned, 1 to 9.",
                "Rockwood K, Song X, MacKnight C, et al. CMAJ 2005;173:489–495; level names as"
                        + " revised in version 2.0 (2020), Dalhousie University. Primary text not"
                        + " vendored in this system.",
                List.of(
                        CalculatorInput.integer("score", "Clinical Frailty Scale score", null,
                                "1 to 9. The scale is a clinician's global judgement and cannot be"
                                        + " derived from other data."),
                        CalculatorInput.optionalInteger("age_years", "Age", "years",
                                "Used only to report the applicability caveat.")),
                List.of(
                        "The official Dalhousie descriptors are copyright and are not reproduced in"
                                + " this system. The level summaries shown here are Impilo paraphrases"
                                + " for disambiguation only — score against the licensed chart, not"
                                + " against this wording.",
                        "The scale was validated in adults aged 65 and over, and its authors state it"
                                + " is not appropriate for younger people or for people whose"
                                + " limitations come from a stable single-system disability."),
                ClinicalFrailtyScale.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            Integer age = in.integer("age_years");
            var result = ClinicalFrailtyScale.fromScore(in.integer("score"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            List<CalculatorOutput> outputs = new ArrayList<>();
            outputs.add(CalculatorOutput.primary("level", "Level", result.level().label(), null));
            outputs.add(CalculatorOutput.secondary("score", "Score", result.level().score(), null));
            outputs.add(CalculatorOutput.secondary("summary", "Level summary",
                    result.level().summary(), null,
                    ClinicalFrailtyScale.OFFICIAL_DESCRIPTORS_LICENSED
                            ? null
                            : "Impilo paraphrase, not the official descriptor."));
            outputs.add(CalculatorOutput.secondary("living_with_frailty",
                    "Living with frailty by the scale's own naming", result.level().livingWithFrailty(),
                    null));

            String applicability = ClinicalFrailtyScale.applicabilityCaveat(age);
            if (applicability != null) {
                outputs.add(CalculatorOutput.secondary("applicability", "Applicability",
                        applicability, null));
            }
            return descriptor.produce(result.contentVersion(), outputs);
        });
    }

    private static CalculatorRegistration cognitiveScreen() {
        var descriptor = new CalculatorDescriptor(
                "cognitive-screen-proportion",
                "Cognitive screen proportion",
                "Geriatrics",
                "Expresses a cognitive screening total as a proportion of that instrument's maximum,"
                        + " so a trend is legible across instruments.",
                "Arithmetic only. No instrument's scoring rules, cut-offs or normative data are"
                        + " reproduced in this system.",
                List.of(
                        CalculatorInput.integer("score", "Total achieved", null, null),
                        CalculatorInput.integer("max_score", "Instrument maximum", null,
                                "The maximum for the instrument actually used. Passing the maximum"
                                        + " for a different instrument makes every band wrong."),
                        CalculatorInput.optionalInteger("cut_off", "Cut-off", null,
                                "Optional, and supplied by the caller on purpose. This system holds"
                                        + " no default cut-off for any instrument: cut-offs move with"
                                        + " education and language and belong in governed content.")),
                List.of(
                        "The bands are arithmetic fifths of the maximum and are not impairment"
                                + " categories. Two patients in the same band on different"
                                + " instruments have nothing in common beyond the fraction.",
                        "No education adjustment is applied. Instruments validated against local"
                                + " education profiles, as several used in Zimbabwe are, must be"
                                + " interpreted against their own norms."),
                CognitiveScreenScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            Integer cutOff = in.integer("cut_off");
            var result = CognitiveScreenScore.of(in.integer("score"), in.integer("max_score"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            List<CalculatorOutput> outputs = new ArrayList<>();
            outputs.add(CalculatorOutput.primary("percent_of_maximum", "Proportion of maximum",
                    result.percentOfMaximum(), "%"));
            outputs.add(CalculatorOutput.primary("band", "Proportion band", result.band().label(),
                    null, "An arithmetic fifth, not an impairment category."));
            Boolean atOrBelow = result.atOrBelowCutOff(cutOff);
            if (atOrBelow != null) {
                outputs.add(CalculatorOutput.secondary("at_or_below_cut_off",
                        "At or below the supplied cut-off", atOrBelow, null,
                        "Compared against the cut-off you supplied, not one held here."));
            }
            return descriptor.produce(result.contentVersion(), outputs);
        });
    }

    // --- pharmacology ----------------------------------------------------------------------------

    private static CalculatorRegistration opioidEquivalent() {
        var descriptor = new CalculatorDescriptor(
                "opioid-oral-morphine-equivalent",
                "Oral morphine equivalent",
                "Pharmacology",
                "Converts one opioid's daily dose to its oral morphine equivalent, as a comparison"
                        + " figure describing how much opioid a person is on.",
                "Faculty of Pain Medicine (RCoA) Opioids Aware dose equivalence table; WHO cancer"
                        + " pain guidelines 2018. Primary texts not vendored in this system.",
                List.of(
                        CalculatorInput.choice("opioid", "Opioid",
                                EnumOptions.of(OpioidEquianalgesic.Opioid.class,
                                        OpioidEquianalgesic.Opioid::displayName,
                                        opioid -> "Dose in " + opioid.doseUnit()),
                                null),
                        CalculatorInput.decimal("dose", "Dose", null,
                                "In the opioid's own unit — micrograms per hour for the fentanyl"
                                        + " patch, milligrams per 24 hours for everything else.")),
                List.of(
                        "This is not a switching dose. Incomplete cross-tolerance means the"
                                + " calculated equivalent is routinely reduced by 25 to 50 per cent"
                                + " before prescribing, and by how much is a clinical decision. A"
                                + " number taken from here straight to a prescription is an overdose.",
                        "The conversion is one-way, to oral morphine. Published tables give"
                                + " different factors depending on the direction of a switch, and"
                                + " there is no inverse here.",
                        "Methadone has no linear equivalent and is refused. Its ratio to morphine"
                                + " rises with the dose being converted from and it accumulates over"
                                + " days; the conversion needs specialist advice."),
                OpioidEquianalgesic.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var opioid = in.enumValue("opioid", OpioidEquianalgesic.Opioid.class);
            var result = OpioidEquianalgesic.oralMorphineEquivalent(opioid, in.decimal("dose"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("oral_morphine_equivalent",
                            "Oral morphine equivalent", result.oralMorphineEquivalentMgPer24h(),
                            OpioidEquianalgesic.UNIT,
                            "A comparison figure, never a switching dose."),
                    CalculatorOutput.secondary("factor_applied", "Factor applied",
                            result.factorApplied(), null),
                    CalculatorOutput.secondary("source", "Source of the factor",
                            result.opioid().source(), null)));
        });
    }
}
