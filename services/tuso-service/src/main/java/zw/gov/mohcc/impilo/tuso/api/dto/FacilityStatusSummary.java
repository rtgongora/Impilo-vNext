package zw.gov.mohcc.impilo.tuso.api.dto;

import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;

/**
 * Lightweight cross-service facility status reference.
 *
 * <p>HAR S1: {@code operational} is the ONLY field a cross-service gate should read to decide
 * whether a facility may transact. It is deliberately stricter than {@code status}: a facility is
 * operational only when it is ACTIVE, its operating status has been confirmed, it is not merely a
 * regulator listing awaiting configuration, and its source-legitimacy composite allows platform
 * access. Consumers that gated on {@code status == "ACTIVE"} alone were treating a regulator's
 * institution listing as an operating licence.</p>
 */
public record FacilityStatusSummary(
        Long facilityId,
        String facilityCode,
        String name,
        String status,
        String operationalStatus,
        FacilityRegulatoryStatus regulatoryStatus,
        boolean operational,
        String operationalVerdict
) {}

