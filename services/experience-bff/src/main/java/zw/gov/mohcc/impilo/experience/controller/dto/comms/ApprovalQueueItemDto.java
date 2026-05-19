package zw.gov.mohcc.impilo.experience.controller.dto.comms;

public record ApprovalQueueItemDto(
        String id,
        String key,
        String name,
        String type,
        String channel,
        String status,
        String updated_at,
        long age_hours,
        boolean sla_breach,
        String priority
) {
}
