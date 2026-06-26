package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Update a serious-AEFI investigation. {@code status} advances the lifecycle
 * (REQUIRED→PLANNED→IN_PROGRESS→INTERIM→FINAL→CLOSED). {@code finalClassification} uses WHO AEFI
 * causality categories on FINAL.
 */
public record UpdateInvestigationRequest(
        String status,
        String assignedTo,
        LocalDate plannedDate,
        Map<String, Object> fieldFindings,
        String finalClassification,
        String summary,
        String note
) {}
