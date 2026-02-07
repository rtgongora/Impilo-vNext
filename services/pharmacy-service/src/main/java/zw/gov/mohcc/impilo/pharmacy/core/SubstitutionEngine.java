package zw.gov.mohcc.impilo.pharmacy.core;

import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;

import java.util.List;
import java.util.UUID;

/**
 * Engine evaluating and applying drug substitutions during dispensing.
 *
 * <p>Checks substitution rules (generic, therapeutic, formulary, dose-form)
 * for a given item and returns available alternatives. Applies the selected
 * substitution and updates the item record.</p>
 */
public interface SubstitutionEngine {

    /**
     * Evaluate whether a substitution is applicable for the given item.
     */
    SubstitutionResult evaluateSubstitution(UUID itemId, String targetDrugCode);

    /**
     * Get all available substitution options for a dispense item.
     */
    List<SubstitutionOption> getSubstitutionOptions(UUID itemId);

    /**
     * Apply a substitution to a dispense item.
     */
    DispenseItemEntity applySubstitution(UUID itemId, String targetDrugCode, String reason);

    /**
     * Result of a substitution evaluation.
     */
    record SubstitutionResult(
            boolean allowed,
            boolean requiresStepUp,
            String reason
    ) {}

    /**
     * Available substitution option for a dispense item.
     */
    record SubstitutionOption(
            String drugCode,
            String drugDisplay,
            String ruleType,
            String reason,
            boolean requiresStepUp
    ) {}
}
