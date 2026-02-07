package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;
import java.util.UUID;

public record StartShiftOptionsResponse(
        List<EligibleWorkspace> workspaces
) {
    public record EligibleWorkspace(
            UUID workspaceId,
            String name,
            String type,
            boolean eligible,
            List<String> ineligibilityReasons
    ) {}
}
