package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Records a MANUAL VigiFlow entry: the reference id the MCAZ officer received after keying the case
 * into the external WHO-UMC VigiFlow system. This is NOT an automated submission.
 */
public record VigiFlowManualEntryRequest(
        @NotBlank String vigiflowReferenceId,
        String note
) {}
