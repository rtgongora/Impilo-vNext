package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        Long facilityId,
        String name,
        String workspaceType,
        boolean ziboValidated,
        String description,
        Map<String, Object> queueConfig,
        Map<String, Object> defaultPanels,
        boolean active,
        List<WorkspaceRuleDto> rules,
        List<DashboardDetail> dashboards,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
    public record DashboardDetail(
            Long id,
            String panelName,
            String panelType,
            Map<String, Object> config,
            int sortOrder,
            boolean active
    ) {}
}
