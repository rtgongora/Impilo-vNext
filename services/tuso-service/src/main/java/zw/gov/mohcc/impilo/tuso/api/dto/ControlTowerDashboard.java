package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;

public record ControlTowerDashboard(
        List<FacilitySummaryResponse> facilities
) {}
