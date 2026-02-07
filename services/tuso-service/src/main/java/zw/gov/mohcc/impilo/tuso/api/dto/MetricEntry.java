package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record MetricEntry(
        @NotBlank(message = "Metric type is required")
        String metricType,

        @NotNull(message = "Metric value is required")
        BigDecimal metricValue,

        String unit,
        Map<String, Object> metadata
) {}
