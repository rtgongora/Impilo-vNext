package zw.gov.mohcc.impilo.experience.controller.dto.comms;

public record ApprovalQueueCountsDto(
        int templates,
        int campaigns,
        int total
) {
}
