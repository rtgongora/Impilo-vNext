package zw.gov.mohcc.impilo.connectorfhir.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RelayBundleRequest(
        @NotBlank String resourceType,
        @NotNull List<Map<String, Object>> entries,
        UUID destinationId) {}
