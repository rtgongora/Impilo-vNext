package zw.gov.mohcc.impilo.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record TransitionRequest(@NotBlank String action, Map<String, Object> output) {}
