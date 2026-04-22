package zw.gov.mohcc.impilo.tuso.api.dto;

import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;

public record FacilityStatusSummary(
        Long facilityId,
        String facilityCode,
        String name,
        String status,
        String operationalStatus,
        FacilityRegulatoryStatus regulatoryStatus
) {}

