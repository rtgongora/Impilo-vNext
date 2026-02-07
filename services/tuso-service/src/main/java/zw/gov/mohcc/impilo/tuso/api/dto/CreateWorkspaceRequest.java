package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateWorkspaceRequest(
        @NotBlank(message = "Workspace name is required")
        @Size(max = 255, message = "Workspace name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Workspace type is required")
        @Size(max = 50, message = "Workspace type must not exceed 50 characters")
        String workspaceType,

        String description,

        Map<String, Object> queueConfig,

        Map<String, Object> defaultPanels,

        @Valid
        List<WorkspaceRuleDto> rules,

        List<String> dashboards
) {}
