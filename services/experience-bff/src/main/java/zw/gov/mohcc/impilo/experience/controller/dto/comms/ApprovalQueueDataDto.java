package zw.gov.mohcc.impilo.experience.controller.dto.comms;

import java.util.List;
import java.util.Map;

public record ApprovalQueueDataDto(
        List<ApprovalQueueItemDto> templates,
        List<ApprovalQueueItemDto> campaigns,
        ApprovalQueueCountsDto counts,
        Map<String, Object> source_health
) {
}
