package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySourceLegitimacyDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Honest per-source facility legitimacy (IATG Wave 1, WS-E).
 *
 * <p>Each authority's verdict about a facility is stored separately — one current row per
 * {@code (facility, source)} — and never collapsed into a single boolean. Upserts overwrite the
 * current row; the previous values are preserved on the emitted outbox event
 * ({@code tuso.facility.source_legitimacy.upserted}) since the existing facility status-history
 * table is owned by the HPA regulatory lifecycle (its {@code regulatory_status} column is
 * NOT NULL and its writer also mutates {@code facility.regulatory_status}) — appending
 * source-legitimacy notes there would fabricate regulatory-status entries.</p>
 *
 * <p>Doctrine rule enforced here: {@link FacilityLegitimacyStatus#GOVERNMENT_OPERATIONAL_EXCEPTION}
 * requires a non-blank reason AND an explicit {@code allowedOnPlatform} decision.</p>
 */
@Service
public class FacilitySourceLegitimacyService {

    /** Outbox event type emitted on every source-legitimacy upsert (carries previous values). */
    public static final String EVENT_UPSERTED = "tuso.facility.source_legitimacy.upserted";

    private static final Logger log = LoggerFactory.getLogger(FacilitySourceLegitimacyService.class);

    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;
    private final FacilityCertificateRepository certificateRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilitySourceLegitimacyService(FacilityRepository facilityRepository,
                                           FacilitySourceLegitimacyRepository legitimacyRepository,
                                           FacilityCertificateRepository certificateRepository,
                                           EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.legitimacyRepository = legitimacyRepository;
        this.certificateRepository = certificateRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Upsert the current verdict of {@code source} about the facility identified by its canonical
     * UUID. Exactly one row per (facility, source): an existing row is overwritten and its previous
     * values are logged and carried on the outbox event.
     */
    @Transactional
    public FacilitySourceLegitimacyDtos.SourceLegitimacyView upsert(
            UUID facilityUuid,
            FacilityLegitimacySource source,
            FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        validate(request);

        FacilitySourceLegitimacyEntity row = legitimacyRepository
                .findByFacilityIdAndSource(facility.getFacilityUuid(), source)
                .orElseGet(FacilitySourceLegitimacyEntity::new);

        Map<String, Object> previous = row.getId() == null ? null : snapshot(row);

        row.setFacilityId(facility.getFacilityUuid());
        row.setSource(source);
        row.setStatus(request.status());
        row.setAsOf(request.asOf() != null ? request.asOf() : Instant.now());
        row.setEvidenceRef(request.evidenceRef());
        // Honest default: absent an explicit decision, a source verdict does NOT grant platform
        // access. (For GOVERNMENT_OPERATIONAL_EXCEPTION explicitness is validated above.)
        row.setAllowedOnPlatform(Boolean.TRUE.equals(request.allowedOnPlatform()));
        row.setReason(request.reason());
        row.setRecordedBy(ctx.actorId());
        row = legitimacyRepository.save(row);

        if (previous != null) {
            log.info("Facility {} source {} legitimacy overwritten: previous={} -> status={} allowedOnPlatform={}",
                    facility.getFacilityUuid(), source, previous, row.getStatus(), row.isAllowedOnPlatform());
        }
        publishUpserted(facility, row, previous, ctx.tenantId(),
                ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return toView(row);
    }

    /**
     * Import-path stamping: same upsert-per-(facility, source) semantics without requiring a second
     * facility lookup, used by {@link FacilityMasterImportService} while it holds the trust context.
     * Never throws — a stamping failure must not fail the master-pack import row.
     */
    @Transactional
    public void stampFromImport(FacilityEntity facility,
                                FacilityLegitimacySource source,
                                FacilityLegitimacyStatus status,
                                boolean allowedOnPlatform,
                                String evidenceRef,
                                String reason,
                                String actorId,
                                UUID tenantId,
                                String correlationId) {
        try {
            if (facility.getFacilityUuid() == null) {
                log.warn("Cannot stamp source legitimacy for facility {} — canonical UUID not assigned yet",
                        facility.getId());
                return;
            }
            FacilitySourceLegitimacyEntity row = legitimacyRepository
                    .findByFacilityIdAndSource(facility.getFacilityUuid(), source)
                    .orElseGet(FacilitySourceLegitimacyEntity::new);

            // A row projected from a governed Ministry decision is not the import's to rewrite
            // (FCV-W0a). Without this guard an ordinary re-import would silently re-stamp
            // PENDING_VERIFICATION/false over a Ministry verdict and take the facility dark — no
            // error, no audit, because this method swallows exceptions by design. The decision
            // table is the system of record; only its projector may move this row.
            if (row.getDecisionId() != null) {
                log.info("Skipping {} legitimacy stamp for facility {} — the row is bound to Ministry "
                                + "decision {} and only the decision projector may change it",
                        source, facility.getFacilityUuid(), row.getDecisionId());
                return;
            }

            Map<String, Object> previous = row.getId() == null ? null : snapshot(row);
            row.setFacilityId(facility.getFacilityUuid());
            row.setSource(source);
            row.setStatus(status);
            row.setAsOf(Instant.now());
            row.setEvidenceRef(evidenceRef);
            row.setAllowedOnPlatform(allowedOnPlatform);
            row.setReason(reason);
            row.setRecordedBy(actorId);
            row = legitimacyRepository.save(row);
            publishUpserted(facility, row, previous, tenantId, correlationId);
        } catch (Exception e) {
            log.warn("Failed to stamp {} legitimacy for facility {}: {}", source, facility.getId(), e.getMessage());
        }
    }

    /** All current per-source verdicts for a facility (canonical UUID), tenant-guarded. */
    @Transactional(readOnly = true)
    public FacilitySourceLegitimacyDtos.SourceLegitimacyListResponse getSourceLegitimacy(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        List<FacilitySourceLegitimacyDtos.SourceLegitimacyView> views =
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facility.getFacilityUuid())
                        .stream().map(FacilitySourceLegitimacyService::toView).toList();
        return new FacilitySourceLegitimacyDtos.SourceLegitimacyListResponse(facility.getFacilityUuid(), views);
    }

    /**
     * Composite truth: existing regulatory status + certificate summary (read-only) + all per-source
     * legitimacy verdicts + the derived platform-access verdict.
     *
     * <p>{@code platformAccessAllowed} = no source row has {@code allowedOnPlatform=false} AND at
     * least one row allows. Any explicit denial blocks; silence never grants.</p>
     */
    /**
     * The single implementation of the platform-access rule (HAR S1). A veto lattice, never a
     * hierarchy: one authority's deny outranks another's allow, and no recorded verdict at all is
     * itself a deny. Callers that need the verdict without the full composite (status summaries,
     * claim review, cross-service gates) MUST use this rather than re-deriving it — the rule was
     * previously duplicated in {@code FacilityClaimService} with a comment hoping the two would not
     * diverge.
     *
     * @param reasons optional sink for human-readable justification; may be {@code null}
     */
    @Transactional(readOnly = true)
    public boolean platformAccessAllowed(UUID facilityUuid, List<String> reasons) {
        List<FacilitySourceLegitimacyEntity> rows =
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid);
        if (rows.isEmpty()) {
            if (reasons != null) {
                reasons.add("No source legitimacy recorded for this facility; "
                        + "platform access is not granted by default");
            }
            return false;
        }
        boolean anyDenies = rows.stream().anyMatch(r -> !r.isAllowedOnPlatform());
        boolean anyAllows = rows.stream().anyMatch(FacilitySourceLegitimacyEntity::isAllowedOnPlatform);
        if (reasons != null) {
            for (FacilitySourceLegitimacyEntity r : rows) {
                reasons.add(r.getSource() + ": " + r.getStatus()
                        + (r.isAllowedOnPlatform() ? " (allows platform operation)" : " (denies platform operation)"));
            }
        }
        return !anyDenies && anyAllows;
    }

    @Transactional(readOnly = true)
    public FacilitySourceLegitimacyDtos.FacilityStatusCompositeResponse getComposite(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        List<FacilitySourceLegitimacyEntity> rows =
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facility.getFacilityUuid());
        List<FacilitySourceLegitimacyDtos.CertificateSummary> certificates =
                certificateRepository.findByFacilityIdOrderByIssueDateDesc(facility.getId())
                        .stream().map(FacilitySourceLegitimacyService::toCertificateSummary).toList();

        boolean anyDenies = rows.stream().anyMatch(r -> !r.isAllowedOnPlatform());
        boolean anyAllows = rows.stream().anyMatch(FacilitySourceLegitimacyEntity::isAllowedOnPlatform);
        boolean platformAccessAllowed = !anyDenies && anyAllows;

        List<String> reasons = new ArrayList<>();
        if (rows.isEmpty()) {
            reasons.add("No source legitimacy recorded for this facility; platform access is not granted by default");
        }
        for (FacilitySourceLegitimacyEntity r : rows) {
            StringBuilder sb = new StringBuilder()
                    .append(r.getSource()).append(": ").append(r.getStatus())
                    .append(r.isAllowedOnPlatform() ? " (allows platform operation)" : " (denies platform operation)");
            if (r.getReason() != null && !r.getReason().isBlank()) {
                sb.append(" — ").append(r.getReason().trim());
            }
            reasons.add(sb.toString());
        }

        return new FacilitySourceLegitimacyDtos.FacilityStatusCompositeResponse(
                facility.getFacilityUuid(),
                facility.getFacilityCode(),
                facility.getName(),
                facility.getRegulatoryStatus(),
                facility.getRegulatoryStatusUpdatedAt(),
                certificates,
                rows.stream().map(FacilitySourceLegitimacyService::toView).toList(),
                platformAccessAllowed,
                reasons);
    }

    private void validate(FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        if (request.status() == FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "GOVERNMENT_OPERATIONAL_EXCEPTION requires a non-blank reason");
            }
            if (request.allowedOnPlatform() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "GOVERNMENT_OPERATIONAL_EXCEPTION requires an explicit allowedOnPlatform decision");
            }
        }
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityUuid));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility: " + facilityUuid);
        }
        return facility;
    }

    /** Emits the upsert event; previous values ride along so the overwrite is fully traceable. */
    private void publishUpserted(FacilityEntity facility, FacilitySourceLegitimacyEntity row,
                                 Map<String, Object> previous, UUID tenantId, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityUuid", facility.getFacilityUuid().toString());
        payload.put("facilityId", facility.getId());
        payload.put("source", row.getSource().name());
        payload.put("status", row.getStatus().name());
        payload.put("asOf", row.getAsOf().toString());
        payload.put("evidenceRef", row.getEvidenceRef());
        payload.put("allowedOnPlatform", row.isAllowedOnPlatform());
        payload.put("reason", row.getReason());
        payload.put("recordedBy", row.getRecordedBy());
        payload.put("previous", previous);

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FACILITY");
        outbox.setAggregateId(String.valueOf(facility.getId()));
        outbox.setEventType(EVENT_UPSERTED);
        outbox.setPayload(payload);
        outbox.setTenantId(tenantId != null ? tenantId.toString() : null);
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(correlationId);
        outbox.setProducer("tuso");
        // v1.1 envelope requirements — a null pod/idempotency key poisons the
        // outbox publisher (it halts the whole queue to preserve ordering).
        outbox.setPodId("national-spine");
        // HAR S7 — the key MUST include the source. A legitimacy backfill stamps more than one row
        // per facility under a single correlation id (e.g. HPA_LEGAL + PLATFORM_OPERATIONAL); with
        // the source omitted those events collide on the idempotency key and a deduping consumer
        // silently drops all but the first, losing the regulator's verdict downstream.
        outbox.setIdempotencyKey(
                "tuso:legitimacy:" + facility.getId() + ":" + row.getSource().name() + ":" + correlationId);
        outbox.setSubjectType("Facility");
        outbox.setSubjectId(String.valueOf(facility.getId()));
        outbox.setPartitionKey(String.valueOf(facility.getId()));
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        outboxRepository.save(outbox);
    }

    private static Map<String, Object> snapshot(FacilitySourceLegitimacyEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", row.getStatus() != null ? row.getStatus().name() : null);
        m.put("asOf", row.getAsOf() != null ? row.getAsOf().toString() : null);
        m.put("evidenceRef", row.getEvidenceRef());
        m.put("allowedOnPlatform", row.isAllowedOnPlatform());
        m.put("reason", row.getReason());
        m.put("recordedBy", row.getRecordedBy());
        return m;
    }

    private static FacilitySourceLegitimacyDtos.SourceLegitimacyView toView(FacilitySourceLegitimacyEntity e) {
        return new FacilitySourceLegitimacyDtos.SourceLegitimacyView(
                e.getSource(), e.getStatus(), e.getAsOf(), e.getEvidenceRef(),
                e.isAllowedOnPlatform(), e.getReason(), e.getRecordedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static FacilitySourceLegitimacyDtos.CertificateSummary toCertificateSummary(FacilityCertificateEntity c) {
        return new FacilitySourceLegitimacyDtos.CertificateSummary(
                c.getCertificateId(), c.getCertificateType(), c.getCertificateNumber(),
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getIssueDate(), c.getExpiryDate(), c.getIssuedUnderAuthority());
    }
}
