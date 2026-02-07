package zw.gov.mohcc.impilo.msika.api.dto;

public record ServiceDetailDto(
    String serviceCategory,
    Integer durationMinutes,
    Boolean requiresSchedule,
    Boolean requiresReferral,
    String facilityLevelMin,
    String specialtyRequired
) {}
