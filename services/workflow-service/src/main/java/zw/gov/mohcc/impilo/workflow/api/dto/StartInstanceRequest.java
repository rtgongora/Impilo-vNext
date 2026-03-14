package zw.gov.mohcc.impilo.workflow.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record StartInstanceRequest(
        @NotNull UUID definitionId,
        String initiatorRef,
        String subjectRef,
        Map<String, Object> context) {}
