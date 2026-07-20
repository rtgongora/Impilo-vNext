package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient.DeidRunResult;
import zw.gov.mohcc.impilo.ndr.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.ndr.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.ndr.domain.ReleaseRunEntity;
import zw.gov.mohcc.impilo.ndr.domain.ReleasedEncounterEntity;
import zw.gov.mohcc.impilo.ndr.repository.GoldEncounterRepository;
import zw.gov.mohcc.impilo.ndr.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.ndr.repository.ReleaseRunRepository;
import zw.gov.mohcc.impilo.ndr.repository.ReleasedEncounterRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the NDR released / analytics zone by driving gold rows through the
 * data-governance de-identification engine and materialising only its authorised
 * output. Bronze and gold are left untouched as the restricted operational zone.
 *
 * <p>The released zone is honest: every row is tokenised (per-dataset HMAC subject,
 * never a raw {@code patient_ref}), direct identifiers stripped, quasi-identifiers
 * generalised, small cells k-anon suppressed, and the whole build gated by an ACTIVE
 * data-use release policy. Deny-by-default: if the engine returns anything other than
 * RELEASED (e.g. no active policy), the previous released zone is left intact and no
 * new rows are materialised — the run is recorded as REJECTED.</p>
 */
@Service
public class NdrReleaseService {

    private static final Logger log = LoggerFactory.getLogger(NdrReleaseService.class);
    private static final String PRODUCER = "ndr-service";
    private static final String SUBJECT_TOKEN_COLUMN = "subject_token";
    private static final String REQUESTED_BY = "ndr-release";
    private static final int GOLD_PAGE_SIZE = 500;
    private static final int MAX_GOLD_PAGES = 200;   // safety cap: 100k gold rows per build

    private final GoldEncounterRepository goldEncounterRepository;
    private final ReleaseRunRepository releaseRunRepository;
    private final ReleasedEncounterRepository releasedEncounterRepository;
    private final DataGovernanceDeidClient deidClient;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NdrReleaseService(GoldEncounterRepository goldEncounterRepository,
                             ReleaseRunRepository releaseRunRepository,
                             ReleasedEncounterRepository releasedEncounterRepository,
                             DataGovernanceDeidClient deidClient,
                             OutboxEventRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.goldEncounterRepository = goldEncounterRepository;
        this.releaseRunRepository = releaseRunRepository;
        this.releasedEncounterRepository = releasedEncounterRepository;
        this.deidClient = deidClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReleaseRunEntity release(UUID tenantId, String datasetRef,
                                    String podId, String correlationId) {
        List<GoldEncounterEntity> gold = fetchAllGold(tenantId);
        List<Map<String, Object>> sourceRows = gold.stream()
                .map(this::toSourceRow)
                .toList();

        ReleaseRunEntity run = new ReleaseRunEntity(tenantId, datasetRef, correlationId);
        run.setRowsIn(sourceRows.size());

        if (sourceRows.isEmpty()) {
            run.setStatus("NO_SOURCE_ROWS");
            releaseRunRepository.save(run);
            log.info("NDR release build skipped — no gold rows [tenant={}, dataset={}]", tenantId, datasetRef);
            return run;
        }

        DeidRunResult result = deidClient.requestRun(
                datasetRef, REQUESTED_BY, sourceRows, tenantId.toString(), podId, correlationId);

        run.setDeidRunId(result.runId());
        run.setManifestId(result.manifestId());
        run.setPolicyVersion(result.policyVersion());
        run.setRowsOut(result.rowsOut());
        run.setRowsSuppressed(result.rowsSuppressed());

        if (!result.isReleased()) {
            // Deny-by-default: the previous released zone is left intact; nothing new is materialised.
            run.setStatus("REJECTED");
            run.setRejectionReason(result.rejectionReason());
            releaseRunRepository.save(run);
            appendOutbox("impilo.ndr.release.rejected.v1", run, tenantId, podId);
            log.warn("NDR release REJECTED by de-id engine [tenant={}, dataset={}, reason={}]",
                    tenantId, datasetRef, result.rejectionReason());
            return run;
        }

        // RELEASED: re-materialise the released zone as the current snapshot for this dataset.
        releasedEncounterRepository.deleteByTenantIdAndDatasetRef(tenantId, datasetRef);
        int materialised = 0;
        for (Map<String, Object> row : result.releasedRows()) {
            Object tokenValue = row.get(SUBJECT_TOKEN_COLUMN);
            if (tokenValue == null || String.valueOf(tokenValue).isBlank()) {
                // Defence in depth: never store a released row without a tokenised subject.
                throw new IllegalStateException(
                        "De-id released row missing subject_token — refusing to materialise release zone");
            }
            ReleasedEncounterEntity released = new ReleasedEncounterEntity(
                    tenantId, datasetRef, run.getReleaseId(), result.manifestId(),
                    String.valueOf(tokenValue), toJson(row));
            releasedEncounterRepository.save(released);
            materialised++;
        }
        run.setRowsOut(materialised);
        run.setStatus("RELEASED");
        releaseRunRepository.save(run);
        appendOutbox("impilo.ndr.release.built.v1", run, tenantId, podId);

        log.info("NDR release zone materialised [tenant={}, dataset={}, rowsIn={}, rowsOut={}, suppressed={}, manifest={}]",
                tenantId, datasetRef, run.getRowsIn(), materialised, run.getRowsSuppressed(), result.manifestId());
        return run;
    }

    private List<GoldEncounterEntity> fetchAllGold(UUID tenantId) {
        List<GoldEncounterEntity> all = new ArrayList<>();
        int pageNo = 0;
        Page<GoldEncounterEntity> page;
        do {
            PageRequest pageRequest = PageRequest.of(pageNo, GOLD_PAGE_SIZE, Sort.by("createdAt").ascending());
            page = goldEncounterRepository.findByTenantId(tenantId, pageRequest);
            all.addAll(page.getContent());
            pageNo++;
        } while (page.hasNext() && pageNo < MAX_GOLD_PAGES);
        return all;
    }

    /**
     * Flatten a gold encounter into a CPID-keyed RAW source row for the de-id engine.
     * The subject reference is placed under {@code cpid} so the engine tokenises it and
     * the raw value never survives; coarse attributes ride along for policy-driven
     * generalisation. No free-text / direct identifiers are emitted by NDR.
     */
    private Map<String, Object> toSourceRow(GoldEncounterEntity gold) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cpid", gold.getPatientRef());   // subject key → tokenised, raw value stripped
        if (gold.getFacilityRef() != null) {
            row.put("facility", gold.getFacilityRef());
        }
        row.put("status", gold.getStatus());
        if (gold.getStartedAt() != null) {
            row.put("visit_date", gold.getStartedAt().toLocalDate().toString());
        }
        return row;
    }

    private void appendOutbox(String eventType, ReleaseRunEntity run, UUID tenantId, String podId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("release_id", run.getReleaseId().toString());
            payload.put("dataset_ref", run.getDatasetRef());
            payload.put("status", run.getStatus());
            payload.put("deid_run_id", run.getDeidRunId() != null ? run.getDeidRunId().toString() : null);
            payload.put("manifest_id", run.getManifestId() != null ? run.getManifestId().toString() : null);
            payload.put("policy_version", run.getPolicyVersion());
            payload.put("rows_in", run.getRowsIn());
            payload.put("rows_out", run.getRowsOut());
            payload.put("rows_suppressed", run.getRowsSuppressed());
            if (run.getRejectionReason() != null) {
                payload.put("rejection_reason", run.getRejectionReason());
            }

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setAggregateType("NdrRelease");
            outbox.setAggregateId(run.getReleaseId().toString());
            outbox.setEventType(eventType);
            outbox.setSchemaVersion("1");
            outbox.setCorrelationId(run.getCorrelationId() != null ? safeUuid(run.getCorrelationId()) : null);
            outbox.setIdempotencyKey("ndr-release-" + run.getReleaseId());
            outbox.setProducer(PRODUCER);
            outbox.setTenantId(tenantId);
            outbox.setPodId(podId != null ? podId : "national-spine");
            outbox.setSubjectId(run.getReleaseId().toString());
            outbox.setSubjectType("NdrRelease");
            outbox.setPartitionKey(run.getDatasetRef());
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(toJson(payload));
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to publish NDR release outbox event [release={}]: {}",
                    run.getReleaseId(), e.getMessage(), e);
        }
    }

    private static UUID safeUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
