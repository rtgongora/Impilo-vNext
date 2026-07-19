package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * HPA practitioner-in-charge candidate importer.
 *
 * <p>Reads the current practitioner-in-charge relationships from the HPA candidate
 * feed and, for each, best-effort resolves the practitioner to an existing varapi
 * provider by council registration number, persisting a CANDIDATE relationship in
 * {@code varapi.hpa_practitioner_candidate}. The import grants NO authority — never
 * employment, scope, Facility Mode, administrative or operational access; the
 * candidate is {@code approval_state=PENDING}, {@code authority_grant=NONE}.</p>
 *
 * <p>Materialisation into the authoritative {@code practitioner_in_charge_assignments}
 * happens only when the provider AND the tuso facility both resolve and a human
 * approves. Unresolved practitioners are onboarding candidates for review.</p>
 */
@Service
public class HpaPractitionerImportService {

    private static final Logger log = LoggerFactory.getLogger(HpaPractitionerImportService.class);

    static final String BUNDLE_ID = "TUSO_HPA_VNEXT_IMPORT_2026_07_19";
    static final String SOURCE_EFFECTIVE_DATE = "2026-07-17";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProviderCouncilRegistrationRecordRepository councilRegRepo;

    public HpaPractitionerImportService(JdbcTemplate jdbc,
                                        ObjectMapper objectMapper,
                                        ProviderCouncilRegistrationRecordRepository councilRegRepo) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.councilRegRepo = councilRegRepo;
    }

    public record ImportRequest(String feedPath, UUID tenantId) {}

    public record ImportSummary(int picTotal, int resolved, int unresolved, int idempotentSkipped) {}

    @Transactional
    public ImportSummary importFeed(ImportRequest request) {
        UUID tenantId = resolveTenant(request.tenantId());
        Path feed = Path.of(request.feedPath());
        if (!Files.isReadable(feed)) {
            throw new IllegalArgumentException("HPA feed not readable: " + request.feedPath());
        }
        int total = 0, resolved = 0, unresolved = 0, skipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(feed, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode record = objectMapper.readTree(line);
                long institutionId = record.path("current_hpa").path("hpa_institution_id").asLong();
                for (JsonNode pic : record.path("current_practitioners_in_charge")) {
                    total++;
                    Outcome o = upsertCandidate(tenantId, institutionId, pic);
                    switch (o) {
                        case RESOLVED -> resolved++;
                        case UNRESOLVED -> unresolved++;
                        case SKIPPED -> skipped++;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("HPA feed read failed: " + e.getMessage(), e);
        }
        log.info("HPA practitioner import: total={} resolved={} unresolved={} idempotent-skipped={}",
                total, resolved, unresolved, skipped);
        return new ImportSummary(total, resolved, unresolved, skipped);
    }

    private enum Outcome { RESOLVED, UNRESOLVED, SKIPPED }

    private Outcome upsertCandidate(UUID tenantId, long institutionId, JsonNode pic) {
        String srk = text(pic, "source_record_key");
        String regNum = text(pic, "practitioner_registration_number_normalized");

        // Best-effort resolve to an existing provider by registration number — resolves a
        // profile, never authenticates or authorises (LOGIN-PROVIDERID-DENY / no authority).
        Long providerId = null;
        String status = "UNRESOLVED";
        if (regNum != null) {
            providerId = councilRegRepo.findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, regNum)
                    .map(r -> r.getProvider() != null ? r.getProvider().getId() : null)
                    .orElse(null);
            if (providerId != null) {
                status = "RESOLVED";
            }
        }

        int rows = jdbc.update(
                "INSERT INTO varapi.hpa_practitioner_candidate (tenant_id, bundle_id, source_record_key, "
                        + "source_effective_date, hpa_institution_id, facility_registration_number, "
                        + "practitioner_sequence, practitioner_name, practitioner_registration_number, "
                        + "relationship_role, resolved_provider_id, varapi_resolution_status, approval_state, "
                        + "authority_grant, onboarding_status) "
                        + "VALUES (?,?,?,?::date,?,?,?,?,?,?,?,?,'PENDING','NONE','OPEN') "
                        + "ON CONFLICT (bundle_id, source_record_key) DO NOTHING",
                tenantId, BUNDLE_ID, srk, SOURCE_EFFECTIVE_DATE, institutionId,
                text(pic, "facility_registration_number_normalized"),
                pic.path("practitioner_sequence").isNumber() ? pic.path("practitioner_sequence").asInt() : null,
                text(pic, "practitioner_name_raw"), regNum,
                text(pic, "relationship_role"), providerId, status);
        if (rows == 0) {
            return Outcome.SKIPPED; // idempotent — candidate already imported
        }
        return "RESOLVED".equals(status) ? Outcome.RESOLVED : Outcome.UNRESOLVED;
    }

    private UUID resolveTenant(UUID requested) {
        if (requested != null) {
            return requested;
        }
        try {
            return TrustContextHolder.require().tenantId();
        } catch (Exception e) {
            throw new IllegalStateException("No tenant context for HPA practitioner import");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
