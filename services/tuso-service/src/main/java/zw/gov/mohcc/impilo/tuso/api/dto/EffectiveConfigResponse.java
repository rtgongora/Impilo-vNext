package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.Map;

public record EffectiveConfigResponse(
        Map<String, Object> config
) {}
