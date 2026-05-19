package zw.gov.mohcc.impilo.experience.controller.dto.comms;

import java.util.Map;

public record RoleDashboardDto(
        Map<String, Object> executive,
        Map<String, Object> operations,
        Map<String, Object> clinical,
        Map<String, Object> communications,
        Map<String, Object> governance
) {
}
