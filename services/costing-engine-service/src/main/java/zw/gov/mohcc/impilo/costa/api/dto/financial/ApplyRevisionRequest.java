package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ApplyRevisionRequest(
        @NotBlank String revisionType,
        String reason,
        @NotEmpty List<LineAdjustmentDto> adjustments
) {
    public record LineAdjustmentDto(UUID lineId, BigDecimal newAmount) {}
}
