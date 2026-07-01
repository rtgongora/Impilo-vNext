package zw.gov.mohcc.impilo.pct.core.forms;

import java.util.List;

/**
 * Resolver input. When {@code encounterId} is supplied, setting/context/cpid are derived from the
 * encounter; explicit values here override. Patient facts (age/sex/pregnant/programmes/conditions) may be
 * supplied explicitly (e.g. from journey/programme context) and otherwise fall back to VITO.
 */
public record FormResolveInput(
        Long encounterId,
        String cpid,
        String role,
        String cadre,
        String scope,
        String visitType,
        Integer acuity,
        String context,
        String accessState,
        String careSetting,
        String careStage,
        String specialty,
        // Optional explicit patient facts (override VITO-derived)
        Integer ageMonths,
        String sex,
        Boolean pregnant,
        List<String> programmes,
        List<String> conditionCodes) {
}
