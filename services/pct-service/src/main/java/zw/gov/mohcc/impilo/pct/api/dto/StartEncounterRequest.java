package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a clinical encounter within a patient journey.
 *
 * <p>An encounter represents a face-to-face interaction between a
 * patient and a provider. The encounter type classifies the nature
 * of the interaction (e.g. CONSULTATION, PROCEDURE, FOLLOW_UP).</p>
 *
 * @param encounterType the type of clinical encounter to initiate
 */
public record StartEncounterRequest(
        @NotBlank String encounterType,
        String encounterContext,
        String entryPoint,
        String modality,
        String virtualMode,
        String careSetting,
        String priority,
        String triageCategory,
        String pathwayRef,
        String protocolRef
) {}
