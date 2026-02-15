package zw.gov.mohcc.impilo.ndr.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record QueryRequest(
        @NotBlank String datasetKey,
        Map<String, String> filters
) {}
