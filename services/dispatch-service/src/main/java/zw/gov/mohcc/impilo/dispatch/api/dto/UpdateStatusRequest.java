package zw.gov.mohcc.impilo.dispatch.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpdateStatusRequest(
        @NotBlank String status,
        Map<String, Object> notes
) {}
