package zw.gov.mohcc.impilo.clinical.calculator.catalog;

import zw.gov.mohcc.impilo.clinical.calculator.CalculatorDescriptor;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorInput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOutput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorRegistration;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorResult;
import zw.gov.mohcc.impilo.medicine.criticalcare.SofaScore;
import zw.gov.mohcc.impilo.medicine.hepatic.ChildPughScore;
import zw.gov.mohcc.impilo.medicine.hepatic.MeldScore;
import zw.gov.mohcc.impilo.medicine.neuro.GlasgowComaScale;
import zw.gov.mohcc.impilo.medicine.respiratory.Curb65Score;
import zw.gov.mohcc.impilo.medicine.thrombosis.WellsDvtScore;
import zw.gov.mohcc.impilo.medicine.thrombosis.WellsPeScore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hepatic, respiratory, thrombosis, neurological and critical-care scores.
 *
 * <p>Several of these are the instruments most often implemented wrongly, and the descriptors say
 * so on the form rather than only in the refusal. A clinician who is told before they start that an
 * unanswered Wells alternative-diagnosis item inflates the score is less likely to leave it blank
 * than one who discovers it from an error message afterwards.</p>
 */
public final class OrganSystemCalculators {

    private OrganSystemCalculators() {
    }

    /** Every calculator this catalogue contributes. */
    public static List<CalculatorRegistration> registrations() {
        List<CalculatorRegistration> all = new ArrayList<>();
        all.add(meld());
        all.add(childPugh());
        all.add(curb65());
        all.add(crb65());
        all.add(wellsDvt());
        all.add(wellsPe());
        all.add(glasgowComaScale());
        all.add(sofa());
        return List.copyOf(all);
    }

    // --- hepatic ---------------------------------------------------------------------------------

    private static CalculatorRegistration meld() {
        var descriptor = new CalculatorDescriptor(
                "meld",
                "MELD",
                "Hepatic",
                "Model for End-stage Liver Disease, the pre-2016 form without sodium.",
                "Kamath PS, Wiesner RH, Malinchoc M, et al. Hepatology 2001;33:464–470, with the UNOS"
                        + " flooring, creatinine cap and dialysis rules. Primary texts not vendored in"
                        + " this system.",
                List.of(
                        CalculatorInput.decimal("bilirubin_umol_l", "Total bilirubin", "µmol/L", null),
                        CalculatorInput.decimal("creatinine_umol_l", "Serum creatinine", "µmol/L", null),
                        CalculatorInput.decimal("inr", "INR", null, null),
                        CalculatorInput.bool("dialysed_twice_in_past_week",
                                "Dialysed twice or more in the past week?",
                                "Must be answered. Dialysis forces the creatinine term to its 4.0 mg/dL"
                                        + " cap, so an unanswered question is not a 'no' — assuming one"
                                        + " understates the score in the sickest patients.")),
                List.of(
                        "MELD describes severity. It does not decide transplant priority, does not"
                                + " predict this patient's mortality, and is not validated in acute"
                                + " liver failure or after transjugular shunting.",
                        "Laboratory values below 1.0 mg/dL are raised to 1.0 before their logarithm is"
                                + " taken; this flooring is part of the published model, not a rounding"
                                + " convenience."),
                MeldScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = MeldScore.fromSiUnits(
                    inputs.decimal("bilirubin_umol_l"),
                    inputs.decimal("creatinine_umol_l"),
                    inputs.decimal("inr"),
                    inputs.bool("dialysed_twice_in_past_week"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("score", "MELD", result.score(), null,
                            result.clampedToPublishedRange()
                                    ? "At a boundary of the published 6–40 range: the raw arithmetic"
                                            + " fell outside it and was clamped."
                                    : null),
                    CalculatorOutput.secondary("creatinine_used_mg_dl", "Creatinine used",
                            result.creatinineUsedMgPerDl(), "mg/dL",
                            Boolean.TRUE.equals(result.dialysisSubstitutionApplied())
                                    ? "Substituted to the 4.0 mg/dL cap because the patient is"
                                            + " dialysed."
                                    : null)));
        });
    }

    private static CalculatorRegistration childPugh() {
        var descriptor = new CalculatorDescriptor(
                "child-pugh",
                "Child-Pugh",
                "Hepatic",
                "The Child-Turcotte-Pugh classification of hepatic function, class A to C.",
                "Pugh RNH, Murray-Lyon IM, Dawson JL, Pietroni MC, Williams R. Br J Surg"
                        + " 1973;60:646–649. Primary text not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("bilirubin_umol_l", "Total bilirubin", "µmol/L", null),
                        CalculatorInput.decimal("albumin_g_l", "Serum albumin", "g/L", null),
                        CalculatorInput.decimal("inr", "INR", null, null),
                        CalculatorInput.choice("ascites", "Ascites",
                                EnumOptions.of(ChildPughScore.Ascites.class,
                                        OrganSystemCalculators::ascitesLabel),
                                "A clinical judgement, not a measurement. There is no 'not assessed'"
                                        + " option: a patient nobody examined is not a patient without"
                                        + " ascites."),
                        CalculatorInput.choice("encephalopathy", "Encephalopathy",
                                EnumOptions.of(ChildPughScore.Encephalopathy.class,
                                        OrganSystemCalculators::encephalopathyLabel),
                                "West Haven grade, collapsed to the three Child-Pugh bands.")),
                List.of(
                        "The class is an input to decisions about operative risk and transplant"
                                + " discussion. It does not estimate survival and does not select a"
                                + " candidate."),
                ChildPughScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = ChildPughScore.of(
                    inputs.decimal("bilirubin_umol_l"),
                    inputs.decimal("albumin_g_l"),
                    inputs.decimal("inr"),
                    inputs.enumValue("ascites", ChildPughScore.Ascites.class),
                    inputs.enumValue("encephalopathy", ChildPughScore.Encephalopathy.class));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("score", "Child-Pugh score", result.score(), null),
                    CalculatorOutput.primary("class", "Child-Pugh class",
                            result.pughClass().label(), null),
                    CalculatorOutput.secondary("bilirubin_points", "Bilirubin points",
                            result.bilirubinPoints(), null),
                    CalculatorOutput.secondary("albumin_points", "Albumin points",
                            result.albuminPoints(), null),
                    CalculatorOutput.secondary("inr_points", "INR points", result.inrPoints(), null),
                    CalculatorOutput.secondary("ascites_points", "Ascites points",
                            result.ascitesPoints(), null),
                    CalculatorOutput.secondary("encephalopathy_points", "Encephalopathy points",
                            result.encephalopathyPoints(), null)));
        });
    }

    private static String ascitesLabel(ChildPughScore.Ascites ascites) {
        return switch (ascites) {
            case NONE -> "None";
            case MILD_DIURETIC_RESPONSIVE -> "Mild, controlled by diuretics";
            case MODERATE_OR_REFRACTORY -> "Moderate, poorly controlled or refractory";
        };
    }

    private static String encephalopathyLabel(ChildPughScore.Encephalopathy encephalopathy) {
        return switch (encephalopathy) {
            case NONE -> "None";
            case GRADE_1_OR_2 -> "West Haven grade 1 or 2";
            case GRADE_3_OR_4 -> "West Haven grade 3 or 4";
        };
    }

    // --- respiratory -----------------------------------------------------------------------------

    private static final List<CalculatorInput> CRB_INPUTS = List.of(
            CalculatorInput.bool("confusion", "New-onset confusion?",
                    "Or an abbreviated mental test score of 8 or less."),
            CalculatorInput.integer("respiratory_rate", "Respiratory rate", "breaths/min", null),
            CalculatorInput.decimal("systolic_mmhg", "Systolic blood pressure", "mmHg", null),
            CalculatorInput.decimal("diastolic_mmhg", "Diastolic blood pressure", "mmHg", null),
            CalculatorInput.integer("age_years", "Age", "years", null));

    private static final List<String> PNEUMONIA_CAVEATS = List.of(
            "The score informs where a pneumonia is managed; it does not decide it. Published"
                    + " guidance subordinates the score to clinical judgement and social"
                    + " circumstance, and a low score does not make an unsafe discharge safe.",
            "The score does not choose an antibiotic.");

    private static CalculatorRegistration curb65() {
        List<CalculatorInput> inputs = new ArrayList<>();
        inputs.add(CRB_INPUTS.get(0));
        inputs.add(CalculatorInput.decimal("urea_mmol_l", "Serum urea", "mmol/L",
                "Required. Scoring the other four items and calling the result CURB-65 reports a"
                        + " maximum of 4 as though it were out of 5. Where there is no chemistry, use"
                        + " CRB-65, which is a separate instrument with its own bands."));
        inputs.addAll(CRB_INPUTS.subList(1, CRB_INPUTS.size()));

        var descriptor = new CalculatorDescriptor(
                "curb-65",
                "CURB-65",
                "Respiratory",
                "Severity score for community-acquired pneumonia, all five items.",
                "Lim WS, van der Eerden MM, Laing R, et al. Thorax 2003;58:377–382. Primary text not"
                        + " vendored in this system.",
                inputs,
                PNEUMONIA_CAVEATS,
                Curb65Score.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = Curb65Score.of(
                    in.bool("confusion"),
                    in.decimal("urea_mmol_l"),
                    in.integer("respiratory_rate"),
                    in.decimal("systolic_mmhg"),
                    in.decimal("diastolic_mmhg"),
                    in.integer("age_years"));
            return curbResult(descriptor, result);
        });
    }

    private static CalculatorRegistration crb65() {
        var descriptor = new CalculatorDescriptor(
                "crb-65",
                "CRB-65",
                "Respiratory",
                "Severity score for community-acquired pneumonia without the urea item, for use where"
                        + " there is no laboratory. Scored out of 4 with its own risk bands.",
                "Lim WS, van der Eerden MM, Laing R, et al. Thorax 2003;58:377–382. Primary text not"
                        + " vendored in this system.",
                CRB_INPUTS,
                List.of(
                        "This is not a CURB-65 with a gap in it. It is a distinct instrument scored"
                                + " out of 4 whose bands differ — a total of 1 is low risk on CURB-65"
                                + " and intermediate here. Display it as CRB-65, never as CURB-65.",
                        PNEUMONIA_CAVEATS.get(0),
                        PNEUMONIA_CAVEATS.get(1)),
                Curb65Score.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = Curb65Score.crb65(
                    in.bool("confusion"),
                    in.integer("respiratory_rate"),
                    in.decimal("systolic_mmhg"),
                    in.decimal("diastolic_mmhg"),
                    in.integer("age_years"));
            return curbResult(descriptor, result);
        });
    }

    private static CalculatorResult curbResult(CalculatorDescriptor descriptor,
                                               Curb65Score.Scoring result) {
        if (!result.computed()) {
            return descriptor.refuse(result.reason().name(), result.explanation());
        }
        List<CalculatorOutput> outputs = new ArrayList<>();
        outputs.add(CalculatorOutput.primary("score",
                result.instrument().label() + " score", result.score(), null,
                "Out of " + result.instrument().maximumScore() + "."));
        outputs.add(CalculatorOutput.primary("band", "Risk band", result.band().name(), null));
        outputs.add(CalculatorOutput.secondary("confusion_point", "Confusion",
                result.confusionPoint(), null));
        outputs.add(CalculatorOutput.secondary("urea_point", "Urea", result.ureaPoint(), null,
                result.ureaPoint() == null
                        ? "Not part of this instrument — absent rather than scored zero."
                        : null));
        outputs.add(CalculatorOutput.secondary("respiratory_point", "Respiratory rate",
                result.respiratoryPoint(), null));
        outputs.add(CalculatorOutput.secondary("pressure_point", "Blood pressure",
                result.pressurePoint(), null));
        outputs.add(CalculatorOutput.secondary("age_point", "Age", result.agePoint(), null));
        return descriptor.produce(result.contentVersion(), outputs);
    }

    // --- thrombosis ------------------------------------------------------------------------------

    private static CalculatorRegistration wellsDvt() {
        var descriptor = new CalculatorDescriptor(
                "wells-dvt",
                "Wells score (DVT)",
                "Thrombosis",
                "Pre-test probability of deep vein thrombosis, two-level model.",
                "Wells PS, Anderson DR, Rodger M, et al. N Engl J Med 2003;349:1227–1235. Primary"
                        + " text not vendored in this system.",
                List.of(
                        CalculatorInput.multiChoice("criteria", "Criteria present",
                                EnumOptions.of(WellsDvtScore.Criterion.class,
                                        WellsDvtScore.Criterion::label),
                                "Select every criterion found. Selecting none is a valid answer"
                                        + " meaning the leg was assessed and nothing was found."),
                        CalculatorInput.bool("alternative_diagnosis_assessed",
                                "Has an alternative diagnosis been considered?",
                                "Must be confirmed. The alternative-diagnosis criterion subtracts two"
                                        + " points, so leaving it unconsidered inflates the score and"
                                        + " can report a patient as 'DVT likely' who is not.")),
                List.of(
                        "The score sets the prior against which a D-dimer or an ultrasound is read."
                                + " It does not order a test, interpret one, rule out DVT, or decide"
                                + " whether to anticoagulate."),
                WellsDvtScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = WellsDvtScore.of(
                    in.enumSet("criteria", WellsDvtScore.Criterion.class),
                    in.bool("alternative_diagnosis_assessed"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("score", "Wells score", result.score(), null),
                    CalculatorOutput.primary("probability", "Pre-test probability",
                            result.probability().name(), null),
                    CalculatorOutput.secondary("criteria_met", "Criteria that scored",
                            result.criteriaMet().stream().map(Enum::name).toList(), null)));
        });
    }

    private static CalculatorRegistration wellsPe() {
        var descriptor = new CalculatorDescriptor(
                "wells-pe",
                "Wells score (PE)",
                "Thrombosis",
                "Pre-test probability of pulmonary embolism, reported on both the two-level and"
                        + " three-level models.",
                "Wells PS, Anderson DR, Rodger M, et al. Thromb Haemost 2000;83:416–420, with the"
                        + " two-level simplification in common use. Primary texts not vendored in this"
                        + " system.",
                List.of(CalculatorInput.multiChoice("criteria", "Criteria present",
                        EnumOptions.of(WellsPeScore.Criterion.class, WellsPeScore.Criterion::label),
                        "Select every criterion found. Selecting none is a valid answer meaning the"
                                + " patient was assessed and nothing was found.")),
                List.of(
                        "Both banding models are reported and they disagree at the boundaries — six"
                                + " points is 'likely' on the two-level model and only 'moderate' on"
                                + " the three-level one. State which model a decision is being made"
                                + " against.",
                        "Four items carry 1.5 points. A score stored as a whole number loses the"
                                + " distinction between 4 and 4.5, which straddles a band boundary.",
                        "The score does not order imaging, apply PERC, interpret a D-dimer, or rule"
                                + " out pulmonary embolism."),
                WellsPeScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = WellsPeScore.of(in.enumSet("criteria", WellsPeScore.Criterion.class));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(), List.of(
                    CalculatorOutput.primary("score", "Wells score", result.score(), null),
                    CalculatorOutput.primary("two_level", "Two-level probability",
                            result.twoLevel().name(), null),
                    CalculatorOutput.primary("three_level", "Three-level probability",
                            result.threeLevel().name(), null),
                    CalculatorOutput.secondary("criteria_met", "Criteria that scored",
                            result.criteriaMet().stream().map(Enum::name).toList(), null)));
        });
    }

    // --- neurological ----------------------------------------------------------------------------

    private static CalculatorRegistration glasgowComaScale() {
        var descriptor = new CalculatorDescriptor(
                "glasgow-coma-scale",
                "Glasgow Coma Scale",
                "Neurological",
                "Eye, verbal and motor responses, with the total where all three could be tested.",
                "Teasdale G, Jennett B. Lancet 1974;2:81–84, with the 2014 structured-approach"
                        + " revision. Primary texts not vendored in this system.",
                List.of(
                        CalculatorInput.choice("eye", "Eye opening",
                                EnumOptions.of(GlasgowComaScale.EyeResponse.class,
                                        GlasgowComaScale.EyeResponse::label,
                                        GlasgowComaScale.EyeResponse::notation),
                                null),
                        CalculatorInput.choice("verbal", "Verbal response",
                                EnumOptions.of(GlasgowComaScale.VerbalResponse.class,
                                        GlasgowComaScale.VerbalResponse::label,
                                        GlasgowComaScale.VerbalResponse::notation),
                                "For an intubated patient choose 'not testable', never 'no verbal"
                                        + " response'. The two produce very different totals and only"
                                        + " one of them is true."),
                        CalculatorInput.choice("motor", "Motor response",
                                EnumOptions.of(GlasgowComaScale.MotorResponse.class,
                                        GlasgowComaScale.MotorResponse::label,
                                        GlasgowComaScale.MotorResponse::notation),
                                null)),
                List.of(
                        "Where a component cannot be tested, no total is derived and the components"
                                + " are reported instead. That is the published convention, not a"
                                + " limitation of this tool.",
                        "The components carry information the total loses: E2V2M5 and E4V1M4 both"
                                + " total 9 and mean different things.",
                        "This is the adult scale. The paediatric adaptation is a different"
                                + " instrument and is not offered here."),
                GlasgowComaScale.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = GlasgowComaScale.of(
                    in.enumValue("eye", GlasgowComaScale.EyeResponse.class),
                    in.enumValue("verbal", GlasgowComaScale.VerbalResponse.class),
                    in.enumValue("motor", GlasgowComaScale.MotorResponse.class));

            if (!result.computed()) {
                // An untestable component is a refusal of the total only. The components remain
                // valid observations and are what should be recorded, so they are returned with it.
                var refused = descriptor.refuse(result.reason().name(), result.explanation());
                if (!result.hasUntestableComponent()) {
                    return refused;
                }
                List<CalculatorOutput> partial = new ArrayList<>(componentOutputs(result));
                partial.add(CalculatorOutput.primary("notation", "Components", result.notation(),
                        null, "Record the components. There is no total when one cannot be tested."));
                return new CalculatorResult(descriptor.id(), false, List.copyOf(partial),
                        refused.refusalReason(), refused.refusalDetail(), descriptor.caveats(),
                        result.contentVersion());
            }

            List<CalculatorOutput> outputs = new ArrayList<>();
            outputs.add(CalculatorOutput.primary("total", "GCS total", result.total(), null));
            outputs.add(CalculatorOutput.primary("notation", "Components", result.notation(), null));
            outputs.add(CalculatorOutput.secondary("severity", "Severity band",
                    result.severity().name(), null));
            outputs.addAll(componentOutputs(result));
            return descriptor.produce(result.contentVersion(), outputs);
        });
    }

    private static List<CalculatorOutput> componentOutputs(GlasgowComaScale.Scoring result) {
        return List.of(
                CalculatorOutput.secondary("eye_points", "Eye", result.eyePoints(), null),
                CalculatorOutput.secondary("verbal_points", "Verbal", result.verbalPoints(), null),
                CalculatorOutput.secondary("motor_points", "Motor", result.motorPoints(), null));
    }

    // --- critical care ---------------------------------------------------------------------------

    private static CalculatorRegistration sofa() {
        var descriptor = new CalculatorDescriptor(
                "sofa",
                "SOFA",
                "Critical care",
                "Sequential Organ Failure Assessment across six organ systems.",
                "Vincent JL, Moreno R, Takala J, et al. Intensive Care Med 1996;22:707–710, with the"
                        + " vasopressor and respiratory-support conventions in current use. Primary"
                        + " texts not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("pao2_fio2", "PaO₂/FiO₂ ratio", "mmHg", null),
                        CalculatorInput.optionalBool("respiratory_support",
                                "Mechanically ventilated or otherwise supported?",
                                "Required once the ratio is below 200, where 3 and 4 points are only"
                                        + " available with support."),
                        CalculatorInput.decimal("platelets", "Platelet count", "×10⁹/L", null),
                        CalculatorInput.decimal("bilirubin_umol_l", "Total bilirubin", "µmol/L", null),
                        CalculatorInput.choice("vasopressor", "Vasopressor support",
                                EnumOptions.of(SofaScore.VasopressorSupport.class,
                                        OrganSystemCalculators::vasopressorLabel),
                                "A pressure that is normal only because of noradrenaline is not a"
                                        + " normal cardiovascular system."),
                        CalculatorInput.optionalDecimal("map_mmhg", "Mean arterial pressure", "mmHg",
                                "Used only when no vasopressor is running."),
                        CalculatorInput.integer("gcs", "Glasgow Coma Scale total", null,
                                "3 to 15. A total of 0 is the signature of a GCS that summed absent"
                                        + " components and is refused."),
                        CalculatorInput.optionalDecimal("creatinine_umol_l", "Serum creatinine",
                                "µmol/L", null),
                        CalculatorInput.optionalDecimal("urine_ml_day", "Urine output", "mL/24h",
                                "Scored alongside creatinine, with the worse of the two taken."
                                        + " Oliguria can precede a creatinine rise.")),
                List.of(
                        "A total is produced only when all six systems could be scored. An unmeasured"
                                + " system is not a healthy one, and scoring it as zero would report a"
                                + " patient with unrecognised organ failure as having none.",
                        "SOFA is read as a change from a baseline, not as an absolute figure. No"
                                + " severity band is offered, deliberately.",
                        "The score does not diagnose sepsis: that requires the infection criterion,"
                                + " which is a clinical judgement outside this instrument."),
                SofaScore.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, in -> {
            var result = SofaScore.of(new SofaScore.Inputs(
                    in.decimal("pao2_fio2"),
                    in.bool("respiratory_support"),
                    in.decimal("platelets"),
                    in.decimal("bilirubin_umol_l"),
                    in.decimal("map_mmhg"),
                    in.enumValue("vasopressor", SofaScore.VasopressorSupport.class),
                    in.integer("gcs"),
                    in.decimal("creatinine_umol_l"),
                    in.decimal("urine_ml_day")));

            List<CalculatorOutput> systemOutputs = new ArrayList<>();
            for (Map.Entry<SofaScore.OrganSystem, Integer> entry : result.systemScores().entrySet()) {
                systemOutputs.add(CalculatorOutput.secondary(
                        entry.getKey().name().toLowerCase(Locale.ROOT),
                        entry.getKey().label(), entry.getValue(), null));
            }

            if (!result.computed()) {
                // The systems that could be scored are real and recordable; only the total is refused.
                var refused = descriptor.refuse(result.reason().name(), result.explanation());
                systemOutputs.add(CalculatorOutput.secondary("unscored_systems",
                        "Systems that could not be scored", result.unscoredSystems(), null));
                return new CalculatorResult(descriptor.id(), false, List.copyOf(systemOutputs),
                        refused.refusalReason(), refused.refusalDetail(), descriptor.caveats(),
                        result.contentVersion());
            }

            List<CalculatorOutput> outputs = new ArrayList<>();
            outputs.add(CalculatorOutput.primary("total", "SOFA total", result.total(), null,
                    "Interpret as a change from this patient's baseline, not as an absolute."));
            outputs.addAll(systemOutputs);
            return descriptor.produce(result.contentVersion(), outputs);
        });
    }

    private static String vasopressorLabel(SofaScore.VasopressorSupport support) {
        return switch (support) {
            case NONE -> "None";
            case LOW_DOSE_DOPAMINE_OR_ANY_DOBUTAMINE ->
                    "Dopamine ≤5 µg/kg/min, or any dobutamine";
            case MEDIUM_DOSE ->
                    "Dopamine >5, or adrenaline/noradrenaline ≤0.1 µg/kg/min";
            case HIGH_DOSE ->
                    "Dopamine >15, or adrenaline/noradrenaline >0.1 µg/kg/min";
        };
    }
}
