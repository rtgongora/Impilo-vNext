package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.domain.SubstitutionRuleType;

/**
 * DTO representing an available substitution option for a dispense item.
 *
 * @param drugCode       the target drug code
 * @param drugDisplay    the human-readable drug name
 * @param ruleType       the type of substitution rule (GENERIC, THERAPEUTIC, FORMULARY, DOSE_FORM)
 * @param reason         the clinical or operational reason for the substitution
 * @param requiresStepUp whether the substitution requires senior pharmacist authorization
 */
public record SubstitutionOptionDto(
        String drugCode,
        String drugDisplay,
        SubstitutionRuleType ruleType,
        String reason,
        boolean requiresStepUp
) {}
