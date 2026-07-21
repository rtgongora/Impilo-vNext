package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HPA national facility enrichment importer.
 *
 * <p>Reconciles the current HPA candidate feed
 * ({@code data/hpa_tuso_candidate_feed.jsonl}, one record per current institution)
 * against the live {@code tuso.facility} estate and either ENRICHES an existing
 * facility (field-level provenance, never silently overwriting stronger/verified
 * values) or CREATES an incomplete {@code REGULATOR_LISTED} facility when no
 * sufficient match exists — never blocking creation for missing address / coords
 * / contacts / hours. Never computes {@code 1,773 + 6,327}: matching against live
 * Tuso is the only comparison set.</p>
 *
 * <p>Reuses {@link FacilityMatchService} (exact code, governed alias via
 * {@code facility_identifier}, trigram name with same-district tightening), the
 * {@code facility}/{@code facility_identifier} JPA repositories, and the V036
 * HPA tables via {@link JdbcTemplate}. Idempotent: a facility we created carries
 * a deterministic {@code HPA_INSTITUTION_ID} identifier, so a re-run matches it
 * and creates nothing new; staging/decision rows are unique per
 * {@code (batch, source_record_key)}.</p>
 */
@Service
public class HpaEnrichmentImportService {

    private static final Logger log = LoggerFactory.getLogger(HpaEnrichmentImportService.class);

    static final String BUNDLE_ID = "TUSO_HPA_VNEXT_IMPORT_2026_07_19";
    static final String SOURCE_EFFECTIVE_DATE = "2026-07-17";
    static final String SYS_HPA_INSTITUTION_ID = "HPA_INSTITUTION_ID";
    static final String SYS_HPA_REGISTRATION_NUMBER = "HPA_REGISTRATION_NUMBER";

    /** The 12 canonical reconciliation outcomes. */
    static final String CONFIRMED_EXISTING_ENRICH = "CONFIRMED_EXISTING_ENRICH";
    static final String NEW_REGULATED_ESTABLISHMENT = "NEW_REGULATED_ESTABLISHMENT";
    static final String NEW_ESTABLISHMENT_SITE_UNRESOLVED = "NEW_ESTABLISHMENT_SITE_UNRESOLVED";
    static final String POSSIBLE_EXISTING_REVIEW = "POSSIBLE_EXISTING_REVIEW";
    static final String IDENTITY_CONFLICT = "IDENTITY_CONFLICT";
    static final String QUARANTINED_DATA_QUALITY = "QUARANTINED_DATA_QUALITY";

    /**
     * Records reconciled per database transaction. The apply commits incrementally
     * in chunks (rather than one estate-wide transaction) so the Hibernate
     * persistence context stays small — a single giant transaction accumulates
     * every created/loaded entity and dirty-checks the whole set on each flush,
     * degrading super-linearly across 6k+ records. Chunking also makes the import
     * resumable: committed facilities carry the deterministic HPA_INSTITUTION_ID,
     * so a re-run matches them and creates nothing new.
     */
    static final int CHUNK_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FacilityMatchService matchService;
    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final FacilityContactRepository contactRepository;
    private final TransactionTemplate txTemplate;

    public HpaEnrichmentImportService(JdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      FacilityMatchService matchService,
                                      FacilityRepository facilityRepository,
                                      FacilityIdentifierRepository identifierRepository,
                                      FacilityContactRepository contactRepository,
                                      PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.matchService = matchService;
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.contactRepository = contactRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** Import request: the server-side feed path + dry-run flag (+ optional explicit tenant for rigs). */
    public record HpaImportRequest(String feedPath, boolean dryRun, UUID tenantId, String feedChecksum) {}

    /** Import summary returned to the caller and mirrored in {@code hpa_import_batch}. */
    public record HpaImportSummary(
            long batchId, boolean dryRun, int candidatesTotal, long liveTusoScanned,
            int enrichedExisting, int createdEstablishment, int createdUnresolvedSite,
            int routedToReview, int quarantined, int unchangedIdempotent,
            Map<String, Integer> countsByOutcome) {}

    // NOT @Transactional: the orchestrator commits per chunk (see flushChunk) so
    // no single transaction spans the whole estate. The batch header and finalise
    // rows are written via JdbcTemplate, which autocommits outside a transaction.
    public HpaImportSummary importFeed(HpaImportRequest request) {
        UUID tenantId = resolveTenant(request.tenantId());
        String actorId = resolveActor();
        Path feed = Path.of(request.feedPath());
        if (!Files.isReadable(feed)) {
            throw new IllegalArgumentException("HPA feed not readable: " + request.feedPath());
        }

        long liveTusoScanned = facilityRepository.countByTenantId(tenantId);
        Long batchId = insertBatch(tenantId, request.dryRun(), request.feedChecksum(), liveTusoScanned, actorId);

        Tallies t = new Tallies();
        List<JsonNode> chunk = new ArrayList<>(CHUNK_SIZE);
        try (BufferedReader reader = Files.newBufferedReader(feed, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                chunk.add(objectMapper.readTree(line));
                if (chunk.size() >= CHUNK_SIZE) {
                    flushChunk(tenantId, actorId, batchId, chunk, request.dryRun(), t);
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                flushChunk(tenantId, actorId, batchId, chunk, request.dryRun(), t);
            }
        } catch (IOException e) {
            updateBatchStatus(batchId, "FAILED");
            throw new IllegalStateException("HPA feed read failed: " + e.getMessage(), e);
        }

        finaliseBatch(batchId, t.total, t.enriched, t.createdEst, t.createdUnresolved,
                t.review, t.quarantine, t.idempotent, t.counts);
        if (t.failed > 0) {
            updateBatchStatus(batchId, "COMPLETED_WITH_FAILURES");
        }
        log.info("HPA import batch={} dryRun={} total={} enriched={} created={} unresolved={} review={} quarantine={} idempotent={} failed={}",
                batchId, request.dryRun(), t.total, t.enriched, t.createdEst, t.createdUnresolved,
                t.review, t.quarantine, t.idempotent, t.failed);
        return new HpaImportSummary(batchId, request.dryRun(), t.total, liveTusoScanned,
                t.enriched, t.createdEst, t.createdUnresolved, t.review, t.quarantine, t.idempotent, t.counts);
    }

    /**
     * Reconcile one chunk in a single transaction. Each chunk transaction gets its
     * own short-lived persistence context (open-in-view is disabled), closed on
     * commit — so no entity state accumulates across chunks and flush cost stays
     * bounded to CHUNK_SIZE records. If the chunk fails as a unit, each record is
     * retried in its own transaction so one poison row cannot drop the rest.
     */
    private void flushChunk(UUID tenantId, String actorId, long batchId,
                            List<JsonNode> chunk, boolean dryRun, Tallies tallies) {
        List<JsonNode> records = List.copyOf(chunk);
        try {
            List<Outcome> outcomes = txTemplate.execute(status -> {
                List<Outcome> os = new ArrayList<>(records.size());
                for (JsonNode record : records) {
                    os.add(reconcile(tenantId, actorId, batchId, record, dryRun));
                }
                return os;
            });
            outcomes.forEach(tallies::add);
        } catch (RuntimeException chunkFailure) {
            log.warn("HPA chunk of {} records failed as a unit — retrying individually: {}",
                    records.size(), chunkFailure.toString());
            for (JsonNode record : records) {
                try {
                    Outcome outcome = txTemplate.execute(status ->
                            reconcile(tenantId, actorId, batchId, record, dryRun));
                    tallies.add(outcome);
                } catch (RuntimeException recordFailure) {
                    tallies.total++;
                    tallies.failed++;
                    log.warn("HPA record failed srk={}: {}",
                            text(record, "source_record_key"), recordFailure.toString());
                }
            }
        }
    }

    /** Mutable running totals for the import — mirrors the HpaImportSummary fields. */
    private static final class Tallies {
        int total, enriched, createdEst, createdUnresolved, review, quarantine, idempotent, failed;
        final Map<String, Integer> counts = new LinkedHashMap<>();

        void add(Outcome o) {
            total++;
            counts.merge(o.decision(), 1, Integer::sum);
            switch (o.category()) {
                case ENRICH -> enriched++;
                case CREATE_ESTABLISHMENT -> createdEst++;
                case CREATE_UNRESOLVED -> createdUnresolved++;
                case REVIEW -> review++;
                case QUARANTINE -> quarantine++;
                case IDEMPOTENT -> idempotent++;
            }
        }
    }

    // ── Reconciliation ──────────────────────────────────────────────────────

    private enum Category { ENRICH, CREATE_ESTABLISHMENT, CREATE_UNRESOLVED, REVIEW, QUARANTINE, IDEMPOTENT }

    private record Outcome(String decision, Category category, Long facilityId) {}

    private Outcome reconcile(UUID tenantId, String actorId, long batchId, JsonNode record, boolean dryRun) {
        JsonNode hpa = record.path("current_hpa");
        String srk = text(record, "source_record_key");
        long institutionId = hpa.path("hpa_institution_id").asLong();
        String name = text(hpa, "institution_name_raw");
        String regNum = text(hpa, "registration_number_normalized");
        String province = text(hpa, "province");
        String siteResolution = text(hpa, "site_resolution_status");
        boolean quarantine = "QUARANTINE".equalsIgnoreCase(text(hpa, "profile_state"))
                || record.path("data_quality_quarantined").asBoolean(false);

        stageCandidate(tenantId, batchId, srk, institutionId, hpa, text(record.path("hpa_current_to_legacy_match"), "status"), record);

        if (quarantine || isBlank(name)) {
            recordDecision(tenantId, batchId, srk, QUARANTINED_DATA_QUALITY, "LOW", null, true, "BLANK_NAME_OR_QUARANTINED");
            return new Outcome(QUARANTINED_DATA_QUALITY, Category.QUARANTINE, null);
        }

        // Idempotency: a facility WE created carries HPA_INSTITUTION_ID = "HPA-<id>".
        String hpaIdValue = "HPA-" + institutionId;
        Optional<FacilityIdentifierEntity> priorHpa = identifierRepository.findBySystemAndValue(SYS_HPA_INSTITUTION_ID, hpaIdValue);
        if (priorHpa.isPresent()) {
            Long fid = priorHpa.get().getFacility().getId();
            recordDecision(tenantId, batchId, srk, CONFIRMED_EXISTING_ENRICH, "HIGH", fid, false, "IDEMPOTENT_PRIOR_HPA_IMPORT");
            return new Outcome(CONFIRMED_EXISTING_ENRICH, Category.IDEMPOTENT, fid);
        }

        // Match against the live estate (reuses the D-L5 matcher).
        Map<String, String> ids = new LinkedHashMap<>();
        if (!isBlank(regNum)) {
            ids.put(SYS_HPA_REGISTRATION_NUMBER, regNum);
        }
        FacilityMatchService.MatchResult match = matchService.matchForRegistration(tenantId, name, null, ids, province);

        if (match.credibleMatch() && !match.candidates().isEmpty()) {
            FacilityMatchService.MatchCandidate top = match.candidates().get(0);
            boolean nameCorroborated = top.score() >= 0.72
                    || "FACILITY_CODE".equals(top.matchType())
                    || top.matchType().startsWith("ALIAS:");
            if (match.candidates().size() == 1 && nameCorroborated) {
                Long fid = top.facility().getId();
                if (!dryRun) {
                    enrichExisting(tenantId, actorId, batchId, fid, hpa, srk, institutionId, hpaIdValue, regNum, corroboratedLegacy(record));
                }
                recordDecision(tenantId, batchId, srk, CONFIRMED_EXISTING_ENRICH, "HIGH", fid, false,
                        "MATCH:" + top.matchType());
                return new Outcome(CONFIRMED_EXISTING_ENRICH, Category.ENRICH, fid);
            }
            // A match exists but is ambiguous or reg-number-only with a name conflict:
            // registration-number alone is NOT conclusive → route to review, never overwrite.
            String decision = nameCorroborated ? POSSIBLE_EXISTING_REVIEW : IDENTITY_CONFLICT;
            recordDecision(tenantId, batchId, srk, decision, "MEDIUM", top.facility().getId(), true,
                    "AMBIGUOUS_OR_REG_ONLY:" + match.candidates().size() + "_CANDIDATES");
            return new Outcome(decision, Category.REVIEW, top.facility().getId());
        }

        // No credible match → create an incomplete regulator-listed record immediately.
        boolean unresolvedSite = siteResolution != null && siteResolution.contains("UNRESOLVED");
        String decision = unresolvedSite ? NEW_ESTABLISHMENT_SITE_UNRESOLVED : NEW_REGULATED_ESTABLISHMENT;
        Long fid = null;
        if (!dryRun) {
            fid = createRegulatorListed(tenantId, actorId, batchId, hpa, srk, institutionId, hpaIdValue, regNum, unresolvedSite, corroboratedLegacy(record));
        }
        recordDecision(tenantId, batchId, srk, decision, "MEDIUM", fid, false, "NO_LIVE_MATCH_CREATE");
        return new Outcome(decision, unresolvedSite ? Category.CREATE_UNRESOLVED : Category.CREATE_ESTABLISHMENT, fid);
    }

    // ── Canonical writes (enrich / create) ──────────────────────────────────

    private void enrichExisting(UUID tenantId, String actorId, long batchId, Long facilityId,
                                JsonNode hpa, String srk, long institutionId, String hpaIdValue, String regNum,
                                JsonNode legacyEnrichment) {
        FacilityEntity facility = facilityRepository.findById(facilityId).orElse(null);
        if (facility == null) {
            return;
        }
        // Stamp the HPA external identifiers (idempotent) — establishes the durable link + idempotency key.
        upsertIdentifier(facility, SYS_HPA_INSTITUTION_ID, hpaIdValue);
        upsertIdentifier(facility, SYS_HPA_REGISTRATION_NUMBER, regNum);
        // Field-level regulatory assertions — dated, never silently overwriting verified operational values.
        assertField(tenantId, batchId, facilityId, "regulatory.status", text(hpa, "regulatory_status"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.council", text(hpa, "regulatory_council"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.type", text(hpa, "institution_type"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.subtype", text(hpa, "institution_subtype"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.designation", text(hpa, "designation"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        setProgressiveState(tenantId, facilityId, "REGULATOR_LISTED", "HPA", srk);
        setProgressiveState(tenantId, facilityId, "PUBLICLY_VISIBLE", "HPA", srk);
        applyLegacyEnrichment(tenantId, batchId, facility, facilityId, srk, legacyEnrichment);
        practitionerInChargeHandoff(tenantId, batchId, facilityId, hpa);
        computeCompletenessAndGaps(tenantId, batchId, facilityId, hpa);
    }

    private Long createRegulatorListed(UUID tenantId, String actorId, long batchId, JsonNode hpa, String srk,
                                       long institutionId, String hpaIdValue, String regNum, boolean unresolvedSite,
                                       JsonNode legacyEnrichment) {
        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(tenantId);
        // Deterministic, stable, unique facility_code so re-runs are idempotent and no code is invented ad hoc.
        facility.setFacilityCode(hpaIdValue);
        facility.setVersion(1);
        facility.setCreatedBy(actorId);
        facility.setName(text(hpa, "institution_name_raw"));
        // facility_type is varchar(50); some HPA institution_type strings exceed it
        // (e.g. combined-discipline labels ~52 chars) — clip to the column width.
        setIfPresent(clip(text(hpa, "institution_type"), 50), facility::setFacilityType);
        setIfPresent(text(hpa, "province"), facility::setProvince);
        setIfPresent(clip(text(hpa, "designation"), 50), facility::setOwnership);
        facility.setDescription("HPA regulator-listed institution (" + BUNDLE_ID + ")");
        // Regulator-listed ≠ operational. Never assume ACTIVE/open from a regulatory listing.
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION);
        facility.setRegulatoryStatusUpdatedAt(Instant.now());
        facility.setStatus("INACTIVE");
        facility.setOperationalStatus("MISSING_REQUIRES_CONFIRMATION");
        facility.setUpdatedBy(actorId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_system", "HPA");
        metadata.put("source_dataset", "HPA_FACILITIES_2026_07_17");
        metadata.put("source_effective_date", SOURCE_EFFECTIVE_DATE);
        metadata.put("hpa_institution_id", institutionId);
        metadata.put("import_bundle_id", BUNDLE_ID);
        metadata.put("verification_status", "REGULATOR_LISTED");
        metadata.put("site_resolution_unresolved", unresolvedSite);
        metadata.put("geospatial_incomplete", true);
        metadata.put("missing_operating_status", true);
        facility.setMetadata(metadata);

        facility = facilityRepository.save(facility);
        Long facilityId = facility.getId();

        upsertIdentifier(facility, SYS_HPA_INSTITUTION_ID, hpaIdValue);
        upsertIdentifier(facility, SYS_HPA_REGISTRATION_NUMBER, regNum);
        assertField(tenantId, batchId, facilityId, "identity.name", text(hpa, "institution_name_raw"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.status", text(hpa, "regulatory_status"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "regulatory.council", text(hpa, "regulatory_council"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        assertField(tenantId, batchId, facilityId, "location.province", text(hpa, "province"), "HPA", srk, "REGULATOR_ASSERTED", "PUBLIC");
        setProgressiveState(tenantId, facilityId, "REGULATOR_LISTED", "HPA", srk);
        setProgressiveState(tenantId, facilityId, "PUBLICLY_VISIBLE", "HPA", srk);
        if (unresolvedSite) {
            dataGapTask(tenantId, batchId, facilityId, "VERIFICATION", "location",
                    "Resolve or link the physical site for this establishment", "STEWARD", "HIGH");
        }
        applyLegacyEnrichment(tenantId, batchId, facility, facilityId, srk, legacyEnrichment);
        practitionerInChargeHandoff(tenantId, batchId, facilityId, hpa);
        computeCompletenessAndGaps(tenantId, batchId, facilityId, hpa);
        return facilityId;
    }

    /**
     * Legacy operational enrichment — ONLY from the corroborated legacy candidate
     * (AUTO_EXACT_REG_CORROBORATED). Facility-scope contacts become UNVERIFIED
     * candidate endpoints (never public until verified); address/locality become
     * dated legacy field-assertions bound for Ndila reconciliation. Personal
     * practitioner/owner contacts are NOT touched here — restricted by policy.
     */
    private void applyLegacyEnrichment(UUID tenantId, long batchId, FacilityEntity facility, Long facilityId,
                                       String srk, JsonNode legacy) {
        if (legacy == null || legacy.isNull() || legacy.isMissingNode()) {
            return;
        }
        upsertFacilityContactCandidate(facility, text(legacy, "facility_telephone"), text(legacy, "facility_email"));
        upsertFacilityContactCandidate(facility, text(legacy, "facility_mobile"), null);
        // Ndila-bound location assertions (dated, unverified legacy — never a public map pin).
        assertField(tenantId, batchId, facilityId, "location.physical_address", text(legacy, "physical_address"),
                "LEGACY_SQL", srk, "UNVERIFIED", "RESTRICTED");
        assertField(tenantId, batchId, facilityId, "location.locality_legacy", text(legacy, "locality"),
                "LEGACY_SQL", srk, "UNVERIFIED", "INTERNAL");
        if (!isBlank(text(legacy, "physical_address"))) {
            upsertCompleteness(tenantId, facilityId, "location", "PARTIAL");
            dataGapTask(tenantId, batchId, facilityId, "VERIFICATION", "location",
                    "Confirm the legacy address and set the map location", "FACILITY", "MEDIUM");
        }
    }

    /**
     * Practitioner-in-charge handoff (Varapi-bound): the current PIC is a
     * REGULATOR-listed candidate relationship that grants NO authority. We record
     * the workforce gap + a verification task; a Varapi consumer resolves each
     * practitioner (by registration number) into a candidate PENDING relationship.
     */
    private void practitionerInChargeHandoff(UUID tenantId, long batchId, Long facilityId, JsonNode hpa) {
        String pic = text(hpa, "practitioners_in_charge_raw");
        if (isBlank(pic)) {
            return;
        }
        upsertCompleteness(tenantId, facilityId, "workforce", "PARTIAL");
        dataGapTask(tenantId, batchId, facilityId, "VERIFICATION", "workforce",
                "Confirm the practitioner-in-charge (resolve via Varapi; grants no authority)", "REGULATOR", "MEDIUM");
    }

    /** Honest incomplete profile: separate completeness per dimension + actionable data-gap tasks. */
    private void computeCompletenessAndGaps(UUID tenantId, long batchId, Long facilityId, JsonNode hpa) {
        upsertCompleteness(tenantId, facilityId, "identity", isBlank(text(hpa, "institution_name_raw")) ? "MISSING" : "COMPLETE");
        upsertCompleteness(tenantId, facilityId, "regulatory", isBlank(text(hpa, "regulatory_status")) ? "PARTIAL" : "COMPLETE");
        upsertCompleteness(tenantId, facilityId, "location", "PARTIAL");     // province only; address/coords awaited
        upsertCompleteness(tenantId, facilityId, "contact", "MISSING");
        upsertCompleteness(tenantId, facilityId, "services", "MISSING");
        upsertCompleteness(tenantId, facilityId, "workforce", "PARTIAL");    // PIC candidate only
        upsertCompleteness(tenantId, facilityId, "access", "MISSING");
        upsertCompleteness(tenantId, facilityId, "emergency_dispatch", "MISSING");
        upsertCompleteness(tenantId, facilityId, "digital_readiness", "MISSING");
        dataGapTask(tenantId, batchId, facilityId, "DATA_GAP", "contact",
                "Confirm public facility contact details", "FACILITY", "MEDIUM");
        dataGapTask(tenantId, batchId, facilityId, "DATA_GAP", "location",
                "Confirm the address and map location", "FACILITY", "MEDIUM");
        dataGapTask(tenantId, batchId, facilityId, "DATA_GAP", "services",
                "Add the services and operating hours", "FACILITY", "LOW");
    }

    // ── V036 table writes (JdbcTemplate) ────────────────────────────────────

    private Long insertBatch(UUID tenantId, boolean dryRun, String checksum, long liveScanned, String actorId) {
        jdbc.update("INSERT INTO tuso.hpa_import_batch (tenant_id, bundle_id, source_effective_date, dry_run, "
                + "feed_checksum, live_tuso_scanned, status, initiated_by) VALUES (?,?,?::date,?,?,?,'RUNNING',?)",
                tenantId, BUNDLE_ID, SOURCE_EFFECTIVE_DATE, dryRun, checksum, liveScanned, actorId);
        return jdbc.queryForObject("SELECT max(id) FROM tuso.hpa_import_batch WHERE tenant_id=? AND bundle_id=?",
                Long.class, tenantId, BUNDLE_ID);
    }

    private void stageCandidate(UUID tenantId, long batchId, String srk, long institutionId, JsonNode hpa,
                                String crosswalkStatus, JsonNode fullRecord) {
        try {
            jdbc.update("INSERT INTO tuso.hpa_candidate_staging (tenant_id, batch_id, source_record_key, "
                    + "hpa_institution_id, institution_name_raw, institution_name_normalized, "
                    + "registration_number_normalized, institution_type, institution_subtype, designation, "
                    + "regulatory_council, regulatory_status, province, current_to_legacy_status, raw_record) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT (batch_id, source_record_key) DO NOTHING",
                    tenantId, batchId, srk, institutionId, text(hpa, "institution_name_raw"),
                    text(hpa, "institution_name_normalized"), text(hpa, "registration_number_normalized"),
                    text(hpa, "institution_type"), text(hpa, "institution_subtype"), text(hpa, "designation"),
                    text(hpa, "regulatory_council"), text(hpa, "regulatory_status"), text(hpa, "province"),
                    crosswalkStatus, fullRecord.toString());
        } catch (Exception e) {
            log.warn("stage candidate {} failed: {}", srk, e.getMessage());
        }
    }

    private void recordDecision(UUID tenantId, long batchId, String srk, String outcome, String confidence,
                                Long facilityId, boolean requiresReview, String reasonCodes) {
        jdbc.update("INSERT INTO tuso.hpa_match_decision (tenant_id, batch_id, staging_id, source_record_key, "
                + "decision_outcome, confidence, matched_facility_id, requires_review, reviewer_status, "
                + "applied, reason_codes) SELECT ?,?, s.id, ?, ?, ?, ?, ?, ?, ?, ? "
                + "FROM tuso.hpa_candidate_staging s WHERE s.batch_id=? AND s.source_record_key=? "
                + "ON CONFLICT (batch_id, source_record_key) DO NOTHING",
                tenantId, batchId, srk, outcome, confidence, facilityId, requiresReview,
                requiresReview ? "PENDING" : "NONE", facilityId != null, reasonCodes, batchId, srk);
    }

    private void assertField(UUID tenantId, long batchId, Long facilityId, String fieldPath, String value,
                             String sourceSystem, String srk, String verificationState, String visibility) {
        if (isBlank(value)) {
            return;
        }
        // Idempotent per (facility, field, source, current): supersede prior current assertion from same source.
        jdbc.update("UPDATE tuso.facility_field_assertion SET is_current=false WHERE facility_id=? AND field_path=? "
                + "AND source_system=? AND is_current=true AND asserted_value IS DISTINCT FROM ?",
                facilityId, fieldPath, sourceSystem, value);
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM tuso.facility_field_assertion WHERE facility_id=? "
                + "AND field_path=? AND source_system=? AND is_current=true AND asserted_value=?",
                Integer.class, facilityId, fieldPath, sourceSystem, value);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update("INSERT INTO tuso.facility_field_assertion (tenant_id, facility_id, field_path, asserted_value, "
                + "source_system, source_record_key, batch_id, source_effective_date, confidence, verification_state, "
                + "public_visibility) VALUES (?,?,?,?,?,?,?,?::date,?,?,?)",
                tenantId, facilityId, fieldPath, value, sourceSystem, srk, batchId, SOURCE_EFFECTIVE_DATE,
                "MEDIUM", verificationState, visibility);
    }

    private void setProgressiveState(UUID tenantId, Long facilityId, String stateKey, String source, String srk) {
        jdbc.update("INSERT INTO tuso.facility_progressive_state (tenant_id, facility_id, state_key, achieved, "
                + "source_system, evidence_ref, achieved_at) VALUES (?,?,?,true,?,?,now()) "
                + "ON CONFLICT (facility_id, state_key) DO UPDATE SET achieved=true, updated_at=now()",
                tenantId, facilityId, stateKey, source, srk);
    }

    private void upsertCompleteness(UUID tenantId, Long facilityId, String dimension, String status) {
        jdbc.update("INSERT INTO tuso.facility_completeness_dimension (tenant_id, facility_id, dimension, status) "
                + "VALUES (?,?,?,?) ON CONFLICT (facility_id, dimension) DO UPDATE SET status=excluded.status, computed_at=now()",
                tenantId, facilityId, dimension, status);
    }

    private void dataGapTask(UUID tenantId, long batchId, Long facilityId, String taskType, String dimension,
                             String requiredInformation, String responsibleActor, String priority) {
        jdbc.update("INSERT INTO tuso.facility_data_gap_task (tenant_id, facility_id, batch_id, task_type, dimension, "
                + "required_information, responsible_actor, priority, visibility, status) "
                + "VALUES (?,?,?,?,?,?,?,?,'PUBLIC','OPEN') ON CONFLICT (facility_id, task_type, required_information) DO NOTHING",
                tenantId, facilityId, batchId, taskType, dimension, requiredInformation, responsibleActor, priority);
    }

    private void finaliseBatch(long batchId, int total, int enriched, int createdEst, int createdUnresolved,
                               int review, int quarantine, int unchanged, Map<String, Integer> counts) {
        String countsJson;
        try {
            countsJson = objectMapper.writeValueAsString(counts);
        } catch (Exception e) {
            countsJson = "{}";
        }
        jdbc.update("UPDATE tuso.hpa_import_batch SET candidates_total=?, enriched_existing=?, "
                + "created_establishment=?, created_unresolved_site=?, routed_to_review=?, quarantined=?, "
                + "unchanged_idempotent=?, counts_by_outcome=?::jsonb, status='COMPLETED', completed_at=now() WHERE id=?",
                total, enriched, createdEst, createdUnresolved, review, quarantine, unchanged, countsJson, batchId);
    }

    private void updateBatchStatus(long batchId, String status) {
        jdbc.update("UPDATE tuso.hpa_import_batch SET status=? WHERE id=?", status, batchId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** The legacy candidate is auto-usable ONLY when the crosswalk corroborated it (feed sets legacy_enrichment). */
    private static JsonNode corroboratedLegacy(JsonNode record) {
        JsonNode legacy = record.path("legacy_enrichment");
        return (legacy.isMissingNode() || legacy.isNull()) ? null : legacy;
    }

    /** Idempotent facility-scope contact candidate (UNVERIFIED); never a personal contact. */
    private void upsertFacilityContactCandidate(FacilityEntity facility, String phone, String email) {
        if (isBlank(phone) && isBlank(email)) {
            return;
        }
        List<FacilityContactEntity> existing = contactRepository.findByFacilityId(facility.getId());
        boolean dup = existing.stream().anyMatch(c ->
                (phone != null && phone.equals(c.getPhone())) || (email != null && email.equals(c.getEmail())));
        if (dup) {
            return;
        }
        FacilityContactEntity contact = new FacilityContactEntity();
        contact.setFacility(facility);
        // Must fit facility_contact.contact_type varchar(30); legacy free-text
        // phone/email are capped to their column widths (50/255) — HPA legacy
        // phone fields can hold several numbers in one string.
        contact.setContactType("HPA_LEGACY_UNVERIFIED");
        contact.setRole("FACILITY");
        if (!isBlank(phone)) {
            contact.setPhone(clip(phone.trim(), 50));
        }
        if (!isBlank(email)) {
            contact.setEmail(clip(email.trim(), 255));
        }
        contactRepository.save(contact);
    }

    private void upsertIdentifier(FacilityEntity facility, String system, String value) {
        if (isBlank(value)) {
            return;
        }
        if (identifierRepository.findBySystemAndValue(system, value.trim()).isPresent()) {
            return;
        }
        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(facility);
        ident.setSystem(system);
        ident.setValue(value.trim());
        ident.setActive(true);
        identifierRepository.save(ident);
    }

    private UUID resolveTenant(UUID requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return TrustContextHolder.require().tenantId();
        } catch (Exception e) {
            throw new IllegalStateException("No tenant context for HPA import");
        }
    }

    private String resolveActor() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (Exception e) {
            return "hpa-import";
        }
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (!isBlank(value)) {
            setter.accept(value.trim());
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Cap a value to a DB column width so legacy free-text never overflows the insert. */
    private static String clip(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
