package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FederationPodRequest(
        @NotBlank String podId,
        @NotBlank String podName,
        @NotBlank @Pattern(regexp = "NATIONAL|SOVEREIGN_MILITARY|SOVEREIGN_PRIVATE|EXPORT") String podType,
        Map<String, Object> authority,
        Map<String, Object> obligations,
        Map<String, Object> routingConfig
) {}
