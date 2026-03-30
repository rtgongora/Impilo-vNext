package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FederationPodResponse(
        Long id,
        String podId,
        String podName,
        String podType,
        String status,
        Map<String, Object> authority,
        Map<String, Object> obligations,
        Map<String, Object> routingConfig,
        Instant registeredAt,
        Instant updatedAt
) {}
