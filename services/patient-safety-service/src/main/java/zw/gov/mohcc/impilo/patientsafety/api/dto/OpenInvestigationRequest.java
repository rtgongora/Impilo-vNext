package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.time.LocalDate;

/** Open a serious-AEFI investigation on a case. */
public record OpenInvestigationRequest(
        String assignedTo,
        LocalDate plannedDate,
        String formPackKey,
        String note
) {}
