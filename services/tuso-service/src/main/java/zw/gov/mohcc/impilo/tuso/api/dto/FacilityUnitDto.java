package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.Map;

/** Facility unit (department) read/write DTOs. */
public final class FacilityUnitDto {

    private FacilityUnitDto() {}

    public record CreateRequest(
            String name,
            String unitType,
            String serviceLine,
            boolean registrationRequired,
            Map<String, Object> metadata
    ) {}

    /** Partial-update request; null fields are left unchanged. */
    public record UpdateRequest(
            String name,
            String unitType,
            String serviceLine,
            Map<String, Object> metadata
    ) {}

    public record Response(
            Long id,
            String name,
            String unitType,
            String serviceLine,
            boolean registrationRequired,
            String regulatoryStatus,
            String certificateStatus
    ) {}
}
