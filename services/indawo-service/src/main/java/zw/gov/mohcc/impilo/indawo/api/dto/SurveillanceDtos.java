package zw.gov.mohcc.impilo.indawo.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Request/response DTOs for the public-health surveillance API. */
public final class SurveillanceDtos {

    private SurveillanceDtos() {}

    // ── Alerts ──
    public record CreateAlertRequest(
            UUID siteId,
            @NotBlank String signalType,
            String conditionCode,
            String severity,
            Integer caseCount,
            String description
    ) {}

    public record AlertResponse(
            String alertId, String siteId, String signalType, String conditionCode,
            String severity, String status, int caseCount, String description, String detectedAt
    ) {}

    // ── Outbreaks ──
    public record CreateOutbreakRequest(
            @NotBlank String conditionCode,
            @NotBlank String name,
            String severity,
            String areaDescription,
            UUID primarySiteId
    ) {}

    public record OutbreakResponse(
            String outbreakId, String conditionCode, String name, String severity, String status,
            String areaDescription, int caseCount, int deathCount, String declaredAt
    ) {}

    // ── Case investigations ──
    public record CreateCaseRequest(
            UUID outbreakId, UUID alertId, UUID siteId,
            String cpid, String classification, String onsetDate, String findings
    ) {}

    public record CaseResponse(
            String caseId, String outbreakId, String cpid, String classification,
            String status, String onsetDate, String investigatedBy
    ) {}

    // ── Field teams ──
    public record CreateTeamRequest(
            @NotBlank String name, String teamType, String leadHealthId, // actor-plane HID
            Integer memberCount, UUID baseSiteId
    ) {}

    public record TeamResponse(
            String teamId, String name, String teamType, String status,
            String leadHealthId, int memberCount // actor-plane HID
    ) {}

    public record DeployRequest(UUID outbreakId, UUID siteId, String objective) {}

    public record DeploymentResponse(
            String deploymentId, String teamId, String outbreakId, String siteId,
            String status, String objective
    ) {}
}
