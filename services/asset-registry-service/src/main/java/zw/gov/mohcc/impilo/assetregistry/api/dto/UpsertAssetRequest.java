package zw.gov.mohcc.impilo.assetregistry.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpsertAssetRequest(
        @NotBlank String facilityRef,
        @NotBlank String type,
        String serialNo,
        String status,
        Map<String, Object> metadata
) {}
