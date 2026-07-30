package zw.gov.mohcc.impilo.clinical.calculator.catalog;

import zw.gov.mohcc.impilo.clinical.calculator.CalculatorDescriptor;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorInput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOption;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOutput;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorRegistration;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorResult;
import zw.gov.mohcc.impilo.medicine.anthropometry.BodyMassIndex;
import zw.gov.mohcc.impilo.medicine.anthropometry.BodySurfaceArea;
import zw.gov.mohcc.impilo.medicine.common.Sex;
import zw.gov.mohcc.impilo.medicine.metabolic.AnionGap;
import zw.gov.mohcc.impilo.medicine.metabolic.CorrectedCalcium;
import zw.gov.mohcc.impilo.medicine.metabolic.CorrectedSodium;
import zw.gov.mohcc.impilo.medicine.renal.CreatinineClearance;
import zw.gov.mohcc.impilo.medicine.renal.EgfrCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Renal, metabolic and anthropometric calculators.
 *
 * <p>Every laboratory input here is declared in the SI units Zimbabwean laboratories report, not in
 * the units the equations were published in. The conversion happens once, inside the domain
 * library, on an entry point written for it. Offering a unit selector on the form would move that
 * conversion to the point of entry, where a mis-set selector is indistinguishable from a correct
 * one after the fact.</p>
 */
public final class RenalMetabolicCalculators {

    private RenalMetabolicCalculators() {
    }

    /** Every calculator this catalogue contributes. */
    public static List<CalculatorRegistration> registrations() {
        List<CalculatorRegistration> all = new ArrayList<>();
        all.add(egfr());
        all.add(creatinineClearance());
        all.add(bodySurfaceArea());
        all.add(bodyMassIndex());
        all.add(correctedCalcium());
        all.add(correctedSodium());
        all.add(anionGap());
        return List.copyOf(all);
    }

    private static final List<CalculatorOption> SEX_OPTIONS =
            EnumOptions.of(Sex.class, sex -> sex == Sex.FEMALE ? "Female" : "Male");

    private static CalculatorRegistration egfr() {
        var descriptor = new CalculatorDescriptor(
                "egfr-ckd-epi-2021",
                "eGFR (CKD-EPI 2021)",
                "Renal",
                "Estimated glomerular filtration rate by the race-free CKD-EPI 2021 equation, with"
                        + " the corresponding KDIGO GFR category.",
                "Inker LA, Eneanya ND, Coresh J, et al. New creatinine- and cystatin C-based equations"
                        + " to estimate GFR without race. N Engl J Med 2021;385:1737–1749. Primary text"
                        + " not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("creatinine_umol_l", "Serum creatinine", "µmol/L",
                                "As reported by the laboratory. Entering a mg/dL value here is the"
                                        + " commonest fault and is refused rather than converted."),
                        CalculatorInput.integer("age_years", "Age", "years", null),
                        CalculatorInput.choice("sex", "Sex", SEX_OPTIONS,
                                "The equation variable. The 2021 equation applies sex-specific"
                                        + " coefficients and carries no race coefficient.")),
                List.of(
                        "eGFR is indexed to a body surface area of 1.73 m². For renal drug dosing,"
                                + " most published tables were derived against creatinine clearance,"
                                + " not eGFR — use the Cockcroft-Gault calculator for those.",
                        "The estimate assumes a steady state and is not valid while the creatinine is"
                                + " still moving, as in acute kidney injury."),
                EgfrCalculator.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = EgfrCalculator.fromCreatinineMicromolPerLitre(
                    inputs.decimal("creatinine_umol_l"),
                    inputs.integer("age_years"),
                    inputs.enumValue("sex", Sex.class));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("egfr", "eGFR", result.egfr(),
                                    "mL/min/1.73m²"),
                            CalculatorOutput.primary("kdigo_stage", "KDIGO GFR category",
                                    result.kdigoStage().name(), null)));
        });
    }

    private static CalculatorRegistration creatinineClearance() {
        var descriptor = new CalculatorDescriptor(
                "creatinine-clearance-cockcroft-gault",
                "Creatinine clearance (Cockcroft-Gault)",
                "Renal",
                "Absolute creatinine clearance in mL/min, the measure most renal drug-dosing tables"
                        + " were derived against.",
                "Cockcroft DW, Gault MH. Prediction of creatinine clearance from serum creatinine."
                        + " Nephron 1976;16:31–41. Primary text not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("creatinine_umol_l", "Serum creatinine", "µmol/L", null),
                        CalculatorInput.integer("age_years", "Age", "years", null),
                        CalculatorInput.decimal("weight_kg", "Actual body weight", "kg",
                                "Actual weight, not ideal or adjusted. No substitution is made"
                                        + " automatically, because which weight to use is a clinical"
                                        + " decision."),
                        CalculatorInput.choice("sex", "Sex", SEX_OPTIONS,
                                "A 0.85 multiplier applies to females.")),
                List.of(
                        "This is not interchangeable with eGFR. Cockcroft-Gault reports an absolute"
                                + " clearance; CKD-EPI reports a rate indexed to 1.73 m². Substituting"
                                + " one for the other misdoses patients at the extremes of body size.",
                        "Because it uses actual body weight, it overestimates clearance in obesity and"
                                + " underestimates it where the weight is oedema or ascites.",
                        "It assumes a steady state and is meaningless in acute kidney injury while the"
                                + " creatinine is still moving."),
                CreatinineClearance.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = CreatinineClearance.fromCreatinineMicromolPerLitre(
                    inputs.decimal("creatinine_umol_l"),
                    inputs.integer("age_years"),
                    inputs.decimal("weight_kg"),
                    inputs.enumValue("sex", Sex.class));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("creatinine_clearance",
                                    "Creatinine clearance", result.clearanceMlPerMin(),
                                    CreatinineClearance.UNIT),
                            CalculatorOutput.secondary("weight_used", "Weight used",
                                    result.weightKg(), "kg"),
                            CalculatorOutput.secondary("creatinine_used_mg_dl",
                                    "Creatinine used, converted", result.creatinineMgPerDl(),
                                    "mg/dL")));
        });
    }

    private static CalculatorRegistration bodySurfaceArea() {
        var descriptor = new CalculatorDescriptor(
                "body-surface-area",
                "Body surface area",
                "Anthropometry",
                "Body surface area by both the DuBois and Mosteller formulae, with the difference"
                        + " between them.",
                "DuBois D, DuBois EF. Arch Intern Med 1916;17:863–871. Mosteller RD. N Engl J Med"
                        + " 1987;317:1098. Primary texts not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("height_cm", "Height", "cm", null),
                        CalculatorInput.decimal("weight_kg", "Weight", "kg", null)),
                List.of(
                        "Both formulae are shown because they disagree, typically by one to three per"
                                + " cent and more at the extremes of body habitus. At chemotherapy doses"
                                + " that is a real difference in milligrams. Record which one was used.",
                        "No dose ceiling is applied. Capping body surface area at 2.0 m², and whether"
                                + " to dose on actual, ideal or adjusted weight, are prescribing"
                                + " decisions and are not made here."),
                BodySurfaceArea.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = BodySurfaceArea.of(inputs.decimal("height_cm"), inputs.decimal("weight_kg"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("dubois", "BSA (DuBois)", result.duBoisM2(),
                                    BodySurfaceArea.UNIT),
                            CalculatorOutput.primary("mosteller", "BSA (Mosteller)",
                                    result.mostellerM2(), BodySurfaceArea.UNIT),
                            CalculatorOutput.secondary("spread", "Difference between formulae",
                                    result.spreadM2(), BodySurfaceArea.UNIT)));
        });
    }

    private static CalculatorRegistration bodyMassIndex() {
        var descriptor = new CalculatorDescriptor(
                "body-mass-index",
                "Body mass index",
                "Anthropometry",
                "Body mass index with the WHO adult classification.",
                "WHO Technical Report Series 894, Geneva 2000, table 2.1. Primary text not vendored"
                        + " in this system.",
                List.of(
                        CalculatorInput.decimal("height_cm", "Height", "cm", null),
                        CalculatorInput.decimal("weight_kg", "Weight", "kg", null),
                        CalculatorInput.integer("age_years", "Age", "years",
                                "Needed to know whether the adult bands apply at all. A child's index"
                                        + " is read against growth references instead.")),
                List.of(
                        "The index cannot distinguish muscle from fat, or peripheral from central"
                                + " adiposity. It over-reads in the muscular and under-reads where lean"
                                + " mass is low, which includes people who have lost weight through"
                                + " chronic illness.",
                        "The WHO cut-points were derived largely in European populations, and the WHO"
                                + " notes that health risk in some Asian populations rises at lower"
                                + " values. No ethnic adjustment is applied here.",
                        "The index is not interpretable in pregnancy. This calculator has no way to"
                                + " know, so a surface filing a BMI must carry that itself.",
                        "Below " + BodyMassIndex.ADULT_FROM_AGE_YEARS + " years the index is still"
                                + " calculated but no band is issued, because the adult table would"
                                + " record a normal childhood value as a nutritional diagnosis."),
                BodyMassIndex.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = BodyMassIndex.of(inputs.decimal("height_cm"), inputs.decimal("weight_kg"),
                    inputs.integer("age_years"));

            if (!result.computed()) {
                // The paediatric case has an index but no band. Returning the arithmetic alongside
                // the refusal lets a surface show the number it did produce without implying the
                // classification it deliberately withheld.
                var refused = descriptor.refuse(result.reason().name(), result.explanation());
                if (!result.hasIndex()) {
                    return refused;
                }
                return new CalculatorResult(descriptor.id(), false,
                        List.of(CalculatorOutput.primary("bmi", "Body mass index", result.bmi(),
                                BodyMassIndex.UNIT,
                                "The arithmetic only. No WHO band applies at this age.")),
                        refused.refusalReason(), refused.refusalDetail(), descriptor.caveats(),
                        result.contentVersion());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("bmi", "Body mass index", result.bmi(),
                                    BodyMassIndex.UNIT),
                            CalculatorOutput.primary("category", "WHO category",
                                    result.category().label(), null),
                            CalculatorOutput.secondary("category_range", "Published interval",
                                    result.category().range(), null)));
        });
    }

    private static CalculatorRegistration correctedCalcium() {
        var descriptor = new CalculatorDescriptor(
                "corrected-calcium",
                "Albumin-corrected calcium",
                "Metabolic",
                "Serum calcium adjusted for the albumin it is bound to, which can unmask a"
                        + " hypercalcaemia that the total calcium hides.",
                "Payne RB, Little AJ, Williams RB, Milner JR. BMJ 1973;4:643–646. Primary text not"
                        + " vendored in this system.",
                List.of(
                        CalculatorInput.decimal("total_calcium_mmol_l", "Total serum calcium",
                                "mmol/L", null),
                        CalculatorInput.decimal("albumin_g_l", "Serum albumin", "g/L",
                                "Required. Without it the uncorrected value is reported as"
                                        + " uncorrected, never presented as though it had been"
                                        + " adjusted.")),
                List.of(
                        "The correction is an approximation fitted on a hospital population and"
                                + " performs poorly in renal failure, acid-base disturbance and"
                                + " critical illness. Where the answer changes management, measure"
                                + " ionised calcium directly."),
                CorrectedCalcium.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = CorrectedCalcium.of(inputs.decimal("total_calcium_mmol_l"),
                    inputs.decimal("albumin_g_l"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("corrected_calcium", "Corrected calcium",
                                    result.correctedCalciumMmolPerL(), CorrectedCalcium.UNIT),
                            CalculatorOutput.secondary("measured_calcium", "Measured total calcium",
                                    result.totalCalciumMmolPerL(), CorrectedCalcium.UNIT),
                            CalculatorOutput.secondary("albumin_used", "Albumin used",
                                    result.albuminGPerL(), "g/L")));
        });
    }

    private static CalculatorRegistration correctedSodium() {
        var descriptor = new CalculatorDescriptor(
                "corrected-sodium",
                "Glucose-corrected sodium",
                "Metabolic",
                "Serum sodium corrected for the dilutional effect of hyperglycaemia, by both the Katz"
                        + " and Hillier factors.",
                "Katz MA. N Engl J Med 1973;289:843–844. Hillier TA, Abbott RD, Barrett EJ. Am J Med"
                        + " 1999;106:399–403. Primary texts not vendored in this system.",
                List.of(
                        CalculatorInput.decimal("sodium_mmol_l", "Measured serum sodium", "mmol/L",
                                null),
                        CalculatorInput.decimal("glucose_mmol_l", "Concurrent serum glucose", "mmol/L",
                                "With a normal glucose there is no dilution to reverse and the"
                                        + " correction is refused rather than applied backwards.")),
                List.of(
                        "Both published factors are shown because they disagree, and at the glucose"
                                + " concentrations seen in diabetic ketoacidosis the gap between them is"
                                + " several millimoles. Hillier's factor fits the measured data better"
                                + " above about 22 mmol/L.",
                        "Correcting says what the sodium would be once the glucose is treated. It does"
                                + " not say the patient is not hyponatraemic, and it never justifies"
                                + " treating the sodium before the glucose."),
                CorrectedSodium.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = CorrectedSodium.of(inputs.decimal("sodium_mmol_l"),
                    inputs.decimal("glucose_mmol_l"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }
            return descriptor.produce(result.contentVersion(),
                    List.of(
                            CalculatorOutput.primary("katz", "Corrected sodium (Katz, 1.6)",
                                    result.katzMmolPerL(), CorrectedSodium.UNIT),
                            CalculatorOutput.primary("hillier", "Corrected sodium (Hillier, 2.4)",
                                    result.hillierMmolPerL(), CorrectedSodium.UNIT),
                            CalculatorOutput.secondary("measured_sodium", "Measured sodium",
                                    result.measuredSodiumMmolPerL(), CorrectedSodium.UNIT)));
        });
    }

    private static CalculatorRegistration anionGap() {
        var descriptor = new CalculatorDescriptor(
                "anion-gap",
                "Anion gap",
                "Metabolic",
                "Serum anion gap, its albumin correction, and the delta ratio where there is a"
                        + " metabolic acidosis to compare.",
                "Standard acid-base physiology; albumin correction after Figge J, Jabor A, Kazda A,"
                        + " Fencl V. Crit Care Med 1998;26:1807–1810. Primary texts not vendored in"
                        + " this system.",
                List.of(
                        CalculatorInput.decimal("sodium_mmol_l", "Serum sodium", "mmol/L", null),
                        CalculatorInput.decimal("chloride_mmol_l", "Serum chloride", "mmol/L", null),
                        CalculatorInput.decimal("bicarbonate_mmol_l", "Serum bicarbonate", "mmol/L",
                                null),
                        CalculatorInput.optionalDecimal("potassium_mmol_l", "Serum potassium",
                                "mmol/L",
                                "Optional. Including potassium raises the gap by roughly four, so the"
                                        + " result states which convention was used."),
                        CalculatorInput.optionalDecimal("albumin_g_l", "Serum albumin", "g/L",
                                "Optional but usually decisive. Albumin is the largest unmeasured"
                                        + " anion, so in hypoalbuminaemia an apparently normal gap can"
                                        + " be a raised one.")),
                List.of(
                        "The delta ratio compares the rise in the gap with the fall in bicarbonate to"
                                + " suggest a coexisting acid-base disturbance. It is computed from two"
                                + " noisy numbers against a population reference and is a hypothesis,"
                                + " not a diagnosis."),
                AnionGap.CONTENT_VERSION);

        return new CalculatorRegistration(descriptor, inputs -> {
            var result = AnionGap.of(
                    inputs.decimal("sodium_mmol_l"),
                    inputs.decimal("chloride_mmol_l"),
                    inputs.decimal("bicarbonate_mmol_l"),
                    inputs.decimal("potassium_mmol_l"),
                    inputs.decimal("albumin_g_l"));

            if (!result.computed()) {
                return descriptor.refuse(result.reason().name(), result.explanation());
            }

            List<CalculatorOutput> outputs = new ArrayList<>();
            outputs.add(CalculatorOutput.primary("anion_gap", "Anion gap",
                    result.anionGapMmolPerL(), AnionGap.UNIT,
                    result.includesPotassium()
                            ? "Potassium-inclusive convention. Reference ranges quoted for the"
                                    + " potassium-free convention do not apply."
                            : "Potassium-free convention."));
            if (result.albuminCorrectionApplied()) {
                outputs.add(CalculatorOutput.primary("albumin_corrected_gap",
                        "Albumin-corrected anion gap", result.albuminCorrectedMmolPerL(),
                        AnionGap.UNIT));
            } else {
                outputs.add(CalculatorOutput.secondary("albumin_corrected_gap",
                        "Albumin-corrected anion gap", null, AnionGap.UNIT,
                        "No albumin was supplied, so no correction was applied. This gap must not be"
                                + " read as corrected."));
            }
            outputs.add(CalculatorOutput.secondary("delta_ratio", "Delta ratio",
                    result.deltaRatio(), null,
                    result.deltaRatio() == null
                            ? "Not computed: the bicarbonate is not below its reference, so there is no"
                                    + " metabolic acidosis to compare against."
                            : null));

            return descriptor.produce(result.contentVersion(), outputs);
        });
    }

}
