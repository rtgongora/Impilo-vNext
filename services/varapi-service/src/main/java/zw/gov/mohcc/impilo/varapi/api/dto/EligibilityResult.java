package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;
import java.util.List;

public record EligibilityResult(
        boolean eligible,
        List<String> reasons,
        LocalDate licenseValidUntil,
        boolean stepUpRequired,
        String providerPublicId
) {}
