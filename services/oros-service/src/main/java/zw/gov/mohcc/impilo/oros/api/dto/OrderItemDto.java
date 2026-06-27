package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for an individual order item within a placement request.
 *
 * <p>The imaging examination fields ({@code modality}, {@code laterality},
 * {@code contrast}, {@code procedureCode}) are optional and only meaningful for
 * {@code IMAGING} order items (spec §4 — examination details).</p>
 */
public record OrderItemDto(
        @NotBlank String code,
        String displayName,
        @Min(1) int quantity,
        String instructions,
        String specimenType,
        String bodySite,
        // ── Imaging examination detail (optional) ──
        String modality,
        String laterality,
        String contrast,
        String procedureCode
) {}
