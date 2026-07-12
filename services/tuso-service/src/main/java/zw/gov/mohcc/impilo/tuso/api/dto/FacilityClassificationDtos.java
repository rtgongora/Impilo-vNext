package zw.gov.mohcc.impilo.tuso.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response DTOs for the facility classification reconciliation surface. */
public final class FacilityClassificationDtos {

    private FacilityClassificationDtos() {}

    public record RunRequest(Boolean dryRun) {}

    public record RunView(Long id, boolean dryRun, int recordsTotal, int recordsCreated, int recordsUpdated,
                          int recordsUnchanged, int recordsPreservedHuman, int recordsFailed,
                          Map<String, Object> countsByStatus, Map<String, Object> estateAudit,
                          List<Map<String, Object>> exceptionReport, String status,
                          String mappingRuleCode, Integer mappingRuleVersion,
                          Instant startedAt, Instant completedAt, String initiatedBy) {}

    public record ProfileView(UUID profileId, Long facilityId,
                              String sourceFacilityType, String sourceOwnership, String sourceLevel,
                              String sourceBedCapacity,
                              String canonicalType, String ownershipAuthority, String hpaRoute,
                              String careSetting, String mobility, String specialistScope,
                              Integer bedCountClaimed, Integer bedCountConfirmed, String bedBand, String bedBandBasis,
                              String hpaClass, String hpaClassJustification,
                              UUID hpaClassificationId, String hpaClassificationLabel,
                              String classificationStatus, Map<String, Object> dimensionStatuses,
                              List<Map<String, Object>> suggestion, List<String> requiredFacts,
                              Map<String, Object> sourceFieldsUsed, String mappingRuleCode, Integer mappingRuleVersion,
                              String unitsReviewStatus, String confirmedBy, Instant confirmedAt,
                              boolean reclassificationReviewRequired) {}

    public record HistoryView(Long historyId, String changeKind, Map<String, Object> prevSnapshot,
                              Map<String, Object> newSnapshot, String actor, Long runId, String note, Instant createdAt) {}

    public record ProfileDetailView(ProfileView profile, List<HistoryView> history) {}

    public record SummaryView(long total, Map<String, Long> byStatus, Map<String, Long> byHpaClass) {}

    /** Supply human-known facts; each is optional but at least one must be present. */
    public record FactsRequest(Integer bedCountConfirmed, String careSetting, String mobility,
                               String specialistScope, String evidenceNote) {}

    public record ConfirmRequest(String note) {}

    /** Human override — a class letter + catalogue label, or NOT_APPLICABLE with a justification. */
    public record CorrectRequest(String hpaClass, String hpaClassificationLabel,
                                 String justification, String note) {}

    public record BulkConfirmRequest(List<String> statuses) {}

    public record BulkConfirmResult(int confirmed, List<String> statuses) {}

    public record UnitCandidateRequest(String suggestedUnitType, String rationale, String evidenceRef) {}

    public record UnitCandidateConfirmRequest(String name, String serviceLine, Boolean picRequired) {}

    public record UnitCandidateView(UUID candidateId, Long facilityId, String suggestedUnitType, String origin,
                                    String servicePresence, String status, String rationale, String evidenceRef,
                                    Long createdUnitId, String proposedBy, String decidedBy, Instant decidedAt,
                                    Instant createdAt) {}
}
