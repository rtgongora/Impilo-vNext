package zw.gov.mohcc.impilo.experience.intake;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.IndawoServiceClient;
import zw.gov.mohcc.impilo.experience.client.LandelaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Redis-backed orchestration for progressive intake, governed bulk import,
 * Indawo site flows, Varapi provider anchoring, and bootstrap probes.
 */
@Service
public class RegistryIntakeService {

    private static final Logger log = LoggerFactory.getLogger(RegistryIntakeService.class);

    private static final String PREFIX_SESSION = "impilo:registry-intake:session:";
    private static final String PREFIX_IMPORT = "impilo:registry-intake:import:";
    private static final String PREFIX_IMPORT_PAYLOAD = "impilo:registry-intake:import-payload:";
    private static final String PREFIX_RECOVERY = "impilo:registry-intake:recovery:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final TusoServiceClient tusoClient;
    private final VarapiServiceClient varapiClient;
    private final VitoServiceClient vitoClient;
    private final IndawoServiceClient indawoClient;
    private final LandelaServiceClient landelaClient;
    private final RegistryIntakeAuthorizationService authorizationService;

    @Autowired(required = false)
    private RegistryIntakeEventPublisher intakeEventPublisher;

    public RegistryIntakeService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${impilo.registry-intake.redis-ttl-days:30}") int ttlDays,
            TusoServiceClient tusoClient,
            VarapiServiceClient varapiClient,
            VitoServiceClient vitoClient,
            IndawoServiceClient indawoClient,
            LandelaServiceClient landelaClient,
            RegistryIntakeAuthorizationService authorizationService) {
        this.redis = redis;
        this.mapper = objectMapper;
        this.ttl = Duration.ofDays(Math.max(1, ttlDays));
        this.tusoClient = tusoClient;
        this.varapiClient = varapiClient;
        this.vitoClient = vitoClient;
        this.indawoClient = indawoClient;
        this.landelaClient = landelaClient;
        this.authorizationService = authorizationService;
    }

    public RegistryIntakeDocuments.IntakeSessionDocument createSession(
            String intakeType,
            String targetRegistry,
            String channel,
            String initiatedBy,
            String provenance,
            String notes) {
        authorizationService.assertSessionMutation();
        String id = UUID.randomUUID().toString();
        RegistryIntakeDocuments.IntakeSessionDocument doc =
                RegistryIntakeDocuments.IntakeSessionDocument.create(
                        id, intakeType, targetRegistry, channel, initiatedBy, provenance, notes);
        writeJson(PREFIX_SESSION + id, doc);
        publish("intake.session.created", Map.of("sessionId", id, "targetRegistry", targetRegistry));
        return doc;
    }

    public Optional<RegistryIntakeDocuments.IntakeSessionDocument> getSession(String id) {
        authorizationService.assertAuthenticated();
        return readJson(PREFIX_SESSION + id, new TypeReference<RegistryIntakeDocuments.IntakeSessionDocument>() {
        });
    }

    public Optional<RegistryIntakeDocuments.IntakeSessionDocument> patchSession(
            String id,
            String workflowState,
            String completionState,
            String linkedHealthId,
            String linkedEntityId,
            String notes) {
        authorizationService.assertSessionMutation();
        return readJson(PREFIX_SESSION + id, new TypeReference<RegistryIntakeDocuments.IntakeSessionDocument>() {
        }).map(existing -> {
            RegistryIntakeDocuments.IntakeSessionDocument updated =
                    existing.withPatch(workflowState, completionState, linkedHealthId, linkedEntityId, notes);
            writeJson(PREFIX_SESSION + id, updated);
            publish("intake.session.updated", Map.of("sessionId", id));
            return updated;
        });
    }

    public RegistryIntakeDocuments.RecoveryRequestDocument createRecoveryRequest(
            String requestKind,
            String targetRegistry,
            String requestedBy,
            String contactHint,
            String narrative) {
        authorizationService.assertSessionMutation();
        String id = UUID.randomUUID().toString();
        RegistryIntakeDocuments.RecoveryRequestDocument doc =
                RegistryIntakeDocuments.RecoveryRequestDocument.create(
                        id, requestKind, targetRegistry, requestedBy, contactHint, narrative);
        writeJson(PREFIX_RECOVERY + id, doc);
        publish("recovery.requested", Map.of("requestId", id, "targetRegistry", targetRegistry));
        return doc;
    }

    public Optional<RegistryIntakeDocuments.RecoveryRequestDocument> getRecovery(String id) {
        authorizationService.assertAuthenticated();
        return readJson(PREFIX_RECOVERY + id, new TypeReference<RegistryIntakeDocuments.RecoveryRequestDocument>() {
        });
    }

    public RegistryIntakeDocuments.ImportJobDocument createImportJob(
            String importType,
            String targetRegistry,
            String inlineCsv,
            String landelaDocumentId,
            String createdBy,
            String notes) {
        authorizationService.assertImportPreview();
        String resolvedCsv = resolveCsvInput(inlineCsv, landelaDocumentId);
        if (resolvedCsv.length() > 400_000) {
            throw new IllegalArgumentException("CSV payload exceeds maximum size (400KB).");
        }
        String id = UUID.randomUUID().toString();
        List<String[]> rows = CsvLineParser.parseRows(resolvedCsv);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV must contain a header row and at least one data row.");
        }
        String[] header = rows.get(0);
        Map<String, Integer> idx = CsvLineParser.headerIndex(header);
        List<Map<String, Object>> preview = new ArrayList<>();
        int dataRows = 0;
        for (int r = 1; r < rows.size(); r++) {
            dataRows++;
            if (preview.size() < 25) {
                preview.add(previewRow(r + 1, rows.get(r), idx, targetRegistry));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("headerColumns", List.of(header));
        summary.put("dataRowCount", dataRows);
        summary.put("governedStatesHint", List.of(
                "PROVISIONAL_IMPORTED", "PENDING_MATCH_REVIEW", "NEEDS_COMPLETION", "UNDER_VERIFICATION"));

        String payloadRef = landelaDocumentId != null && !landelaDocumentId.isBlank() ? landelaDocumentId : null;
        String payloadSource = payloadRef != null ? "LANDELA" : "INLINE";

        RegistryIntakeDocuments.ImportJobDocument job =
                RegistryIntakeDocuments.ImportJobDocument.newJob(
                        id, importType, targetRegistry, createdBy, dataRows, summary, preview, notes,
                        payloadRef, payloadSource);
        writeJson(PREFIX_IMPORT + id, job);
        redis.opsForValue().set(PREFIX_IMPORT_PAYLOAD + id, resolvedCsv, ttl);
        publish("import.job.created", Map.of("jobId", id, "targetRegistry", targetRegistry, "rows", dataRows));
        return job;
    }

    public Optional<RegistryIntakeDocuments.ImportJobDocument> getImportJob(String id) {
        authorizationService.assertAuthenticated();
        return readJson(PREFIX_IMPORT + id, new TypeReference<RegistryIntakeDocuments.ImportJobDocument>() {
        });
    }

    public List<RegistryIntakeDocuments.ImportJobDocument> listImportJobs(int limit) {
        authorizationService.assertAuthenticated();
        Set<String> keys = redis.keys(PREFIX_IMPORT + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        return keys.stream()
                .map(key -> readJson(key, new TypeReference<RegistryIntakeDocuments.ImportJobDocument>() {
                }))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(RegistryIntakeDocuments.ImportJobDocument::updatedAt).reversed())
                .limit(boundedLimit)
                .toList();
    }

    public Optional<RegistryIntakeDocuments.ImportJobDocument> cancelImportJob(String id) {
        authorizationService.assertImportPreview();
        return getImportJob(id).map(existing -> {
            if ("EXECUTED".equals(existing.status()) || "DRY_RUN_COMPLETE".equals(existing.status())) {
                throw new IllegalStateException("Executed import jobs cannot be cancelled.");
            }
            RegistryIntakeDocuments.ImportJobDocument cancelled = existing.withStatus("CANCELLED");
            writeJson(PREFIX_IMPORT + id, cancelled);
            redis.delete(PREFIX_IMPORT_PAYLOAD + id);
            publish("import.job.cancelled", Map.of("jobId", id, "targetRegistry", existing.targetRegistry()));
            return cancelled;
        });
    }

    /**
     * Executes import job for FACILITY (Tuso) or SITE (Indawo) registries.
     */
    public RegistryIntakeDocuments.ImportJobDocument executeImportJob(String jobId, boolean dryRun) {
        authorizationService.assertImportExecute();
        RegistryIntakeDocuments.ImportJobDocument job = getImportJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
        String tr = job.targetRegistry() != null ? job.targetRegistry().toUpperCase(Locale.ROOT) : "";
        if ("FACILITY".equals(tr) || "TUSO".equals(tr)) {
            return executeFacilityImport(jobId, dryRun, job);
        }
        if ("SITE".equals(tr) || "INDAWO".equals(tr)) {
            return executeSiteImport(jobId, dryRun, job);
        }
        throw new IllegalArgumentException("Unsupported import targetRegistry for execute: " + job.targetRegistry());
    }

    private RegistryIntakeDocuments.ImportJobDocument executeFacilityImport(
            String jobId, boolean dryRun, RegistryIntakeDocuments.ImportJobDocument job) {
        String csv = redis.opsForValue().get(PREFIX_IMPORT_PAYLOAD + jobId);
        if (csv == null || csv.isBlank()) {
            throw new IllegalStateException("Import payload missing or expired for job " + jobId);
        }
        List<String[]> rows = CsvLineParser.parseRows(csv);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("Nothing to import.");
        }
        Map<String, Integer> idx = CsvLineParser.headerIndex(rows.get(0));
        List<Map<String, Object>> results = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int rowNumber = r + 1;
            String name = CsvLineParser.cell(row, idx, "name", "facilityName", "facilityname");
            String code = CsvLineParser.cell(row, idx, "facilityCode", "code", "facilitycode");
            String type = CsvLineParser.cell(row, idx, "facilityType", "type");
            String province = CsvLineParser.cell(row, idx, "province");
            String district = CsvLineParser.cell(row, idx, "district");
            if (name.isBlank() || code.isBlank()) {
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "FAILED", "INVALID", "name and facilityCode are required", null, null));
                continue;
            }
            try {
                Map<String, Object> searchBody = new LinkedHashMap<>();
                searchBody.put("query", code);
                searchBody.put("facilityType", null);
                searchBody.put("status", null);
                searchBody.put("province", null);
                searchBody.put("district", null);
                searchBody.put("level", null);
                searchBody.put("page", 0);
                searchBody.put("size", 10);
                JsonNode dup = tusoClient.searchFacilities(searchBody);
                if (dup != null && dup.has("items") && dup.get("items").isArray() && dup.get("items").size() > 0) {
                    results.add(RegistryIntakeDocuments.rowResult(
                            rowNumber,
                            "DUPLICATE_CANDIDATE",
                            "MATCH",
                            "Facility code already present in registry search",
                            null,
                            dup.get("items").get(0).toString()));
                    continue;
                }
            } catch (Exception e) {
                log.debug("Duplicate search failed row {}: {}", rowNumber, e.getMessage());
            }
            if (dryRun) {
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "WOULD_CREATE", "OK", null, null, null));
                continue;
            }
            try {
                Map<String, Object> create = new LinkedHashMap<>();
                create.put("name", name);
                create.put("facilityCode", code);
                create.put("facilityType", type.isBlank() ? null : type);
                create.put("province", province.isBlank() ? null : province);
                create.put("district", district.isBlank() ? null : district);
                create.put("latitude", null);
                create.put("longitude", null);
                create.put("ownership", null);
                create.put("level", null);
                create.put("facilityTier", null);
                create.put("deploymentMode", null);
                create.put("continuityClass", null);
                create.put("workflowArchetype", null);
                create.put("parentId", null);
                create.put("description", "Imported via governed BFF import job " + jobId);
                create.put("openedDate", null);
                create.put("identifiers", null);
                create.put("contacts", null);
                create.put("capabilities", null);
                JsonNode created = tusoClient.createFacility(create);
                String ref = created != null && created.has("id") ? String.valueOf(created.get("id").asLong()) : code;
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "CREATED", "OK", null, ref, null));
            } catch (Exception e) {
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "FAILED", "DOWNSTREAM", e.getMessage(), null, null));
            }
        }
        return finalizeImportJob(jobId, job, results);
    }

    private RegistryIntakeDocuments.ImportJobDocument executeSiteImport(
            String jobId, boolean dryRun, RegistryIntakeDocuments.ImportJobDocument job) {
        String csv = redis.opsForValue().get(PREFIX_IMPORT_PAYLOAD + jobId);
        if (csv == null || csv.isBlank()) {
            throw new IllegalStateException("Import payload missing or expired for job " + jobId);
        }
        List<String[]> rows = CsvLineParser.parseRows(csv);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("Nothing to import.");
        }
        Map<String, Integer> idx = CsvLineParser.headerIndex(rows.get(0));
        List<Map<String, Object>> results = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int rowNumber = r + 1;
            String name = CsvLineParser.cell(row, idx, "name", "siteName", "sitename");
            String type = CsvLineParser.cell(row, idx, "type", "siteType");
            String status = CsvLineParser.cell(row, idx, "status");
            if (name.isBlank()) {
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "FAILED", "INVALID", "name is required", null, null));
                continue;
            }
            String typeVal = type.isBlank() ? "PUBLIC_HEALTH_SITE" : type;
            String statusVal = status.isBlank() ? "PROVISIONAL_IMPORTED" : status;
            try {
                JsonNode dup = findIndawoSiteDuplicate(name);
                if (dup != null) {
                    results.add(RegistryIntakeDocuments.rowResult(
                            rowNumber, "DUPLICATE_CANDIDATE", "MATCH",
                            "Possible duplicate site name in first pages of registry",
                            null, dup.toString()));
                    continue;
                }
            } catch (Exception e) {
                log.debug("Indawo duplicate scan failed row {}: {}", rowNumber, e.getMessage());
            }
            if (dryRun) {
                results.add(RegistryIntakeDocuments.rowResult(rowNumber, "WOULD_CREATE", "OK", null, null, null));
                continue;
            }
            try {
                UUID siteId = UUID.nameUUIDFromBytes((jobId + ":" + rowNumber + ":" + name).getBytes(StandardCharsets.UTF_8));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", name);
                body.put("type", typeVal);
                body.put("address", Map.of());
                body.put("geo", Map.of());
                body.put("status", statusVal);
                JsonNode created = indawoClient.upsertSite(siteId, body);
                String ref = siteId.toString();
                if (created != null && created.has("siteId")) {
                    ref = created.get("siteId").asText();
                }
                results.add(RegistryIntakeDocuments.rowResult(rowNumber, "CREATED", "OK", null, ref, null));
            } catch (Exception e) {
                results.add(RegistryIntakeDocuments.rowResult(
                        rowNumber, "FAILED", "DOWNSTREAM", e.getMessage(), null, null));
            }
        }
        return finalizeImportJob(jobId, job, results);
    }

    private RegistryIntakeDocuments.ImportJobDocument finalizeImportJob(
            String jobId,
            RegistryIntakeDocuments.ImportJobDocument job,
            List<Map<String, Object>> results) {
        long failed = results.stream().filter(m -> "FAILED".equals(m.get("status"))).count();
        long dups = results.stream().filter(m -> "DUPLICATE_CANDIDATE".equals(m.get("status"))).count();
        String status = failed > 0 ? "COMPLETED_WITH_ERRORS" : (dups > 0 ? "COMPLETED_WITH_REVIEW" : "COMPLETED");
        RegistryIntakeDocuments.ImportJobDocument updated = job.withRowResults(results, status);
        writeJson(PREFIX_IMPORT + jobId, updated);
        publish("import.job.completed", Map.of("jobId", jobId, "status", status));
        return updated;
    }

    private JsonNode findIndawoSiteDuplicate(String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        int cursor = 0;
        int limit = 50;
        for (int page = 0; page < 20; page++) {
            JsonNode list = indawoClient.listSites(cursor, limit);
            if (list == null || !list.has("items") || !list.get("items").isArray()) {
                return null;
            }
            for (JsonNode item : list.get("items")) {
                String n = item.has("name") ? item.get("name").asText("").trim().toLowerCase(Locale.ROOT) : "";
                if (!n.isEmpty() && n.equals(needle)) {
                    return item;
                }
            }
            if (!list.has("has_more") || !list.get("has_more").asBoolean()) {
                break;
            }
            cursor++;
        }
        return null;
    }

    public Map<String, Object> searchIndawoSites(String query, int cursor, int limit) {
        authorizationService.assertAuthenticated();
        JsonNode raw = indawoClient.listSites(cursor, Math.min(Math.max(limit, 1), 200));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("raw", raw);
        if (query == null || query.isBlank() || raw == null || !raw.has("items")) {
            return out;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode item : raw.get("items")) {
            String name = item.has("name") ? item.get("name").asText("") : "";
            if (name.toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(item);
            }
        }
        out.put("nameMatches", matches);
        return out;
    }

    /**
     * Creates a Varapi provider profile anchored on an existing Impilo Health ID (Vito client registry).
     */
    public JsonNode createProviderFromHealthId(Map<String, Object> body) {
        authorizationService.assertProviderOrchestration();
        Object hid = body.get("impiloHealthId");
        if (hid == null || hid.toString().isBlank()) {
            throw new IllegalArgumentException("impiloHealthId is required");
        }
        String healthId = hid.toString().trim();
        try {
            JsonNode profile = vitoClient.getClientRegistryProfile(healthId);
            if (profile == null || profile.isNull()) {
                throw new IllegalArgumentException("Person not found in Vito client registry for impiloHealthId=" + healthId);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve Impilo anchor in Vito: " + e.getMessage(), e);
        }
        Map<String, Object> request = new LinkedHashMap<>(body);
        request.putIfAbsent("impiloHealthId", healthId);
        JsonNode created = varapiClient.createProvider(request);
        publish("provider.intake.created", Map.of("impiloHealthId", healthId));
        return created;
    }

    public Map<String, Object> bootstrapSnapshot() {
        authorizationService.assertAuthenticated();
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("generatedAt", java.time.Instant.now().toString());
        snap.put("vito", probeVito());
        snap.put("varapi", probeVarapi());
        snap.put("tuso", probeTuso());
        snap.put("indawo", probeIndawo());
        return snap;
    }

    private String resolveCsvInput(String inlineCsv, String landelaDocumentId) {
        if (landelaDocumentId != null && !landelaDocumentId.isBlank()) {
            return landelaClient.downloadDocumentAsUtf8Text(UUID.fromString(landelaDocumentId.trim()));
        }
        return inlineCsv != null ? inlineCsv : "";
    }

    private void publish(String type, Map<String, Object> payload) {
        if (intakeEventPublisher != null) {
            intakeEventPublisher.publish(type, payload);
        }
    }

    private Map<String, Object> probeVito() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            JsonNode dash = vitoClient.getClientRegistryDashboard();
            m.put("reachable", true);
            m.put("dashboard", dash);
        } catch (Exception e) {
            m.put("reachable", false);
            m.put("error", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> probeVarapi() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", "");
            body.put("profession", null);
            body.put("status", null);
            body.put("page", 0);
            body.put("size", 1);
            JsonNode p = varapiClient.searchProviders(body);
            m.put("reachable", true);
            m.put("sampleTotalElements",
                    p != null && p.has("totalElements") ? p.get("totalElements").asLong() : null);
        } catch (Exception e) {
            m.put("reachable", false);
            m.put("error", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> probeTuso() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", "");
            body.put("facilityType", null);
            body.put("status", null);
            body.put("province", null);
            body.put("district", null);
            body.put("level", null);
            body.put("page", 0);
            body.put("size", 1);
            JsonNode p = tusoClient.searchFacilities(body);
            m.put("reachable", true);
            m.put("sampleTotalElements",
                    p != null && p.has("totalElements") ? p.get("totalElements").asLong() : null);
        } catch (Exception e) {
            m.put("reachable", false);
            m.put("error", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> probeIndawo() {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            JsonNode p = indawoClient.listSites(0, 1);
            m.put("reachable", true);
            m.put("sample", p);
        } catch (Exception e) {
            m.put("reachable", false);
            m.put("error", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> previewRow(int rowNumber, String[] row, Map<String, Integer> idx, String targetRegistry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowNumber", rowNumber);
        String tr = targetRegistry != null ? targetRegistry.toUpperCase(Locale.ROOT) : "";
        if ("SITE".equals(tr) || "INDAWO".equals(tr)) {
            m.put("name", CsvLineParser.cell(row, idx, "name", "siteName"));
            m.put("type", CsvLineParser.cell(row, idx, "type", "siteType"));
            m.put("status", CsvLineParser.cell(row, idx, "status"));
            return m;
        }
        m.put("name", CsvLineParser.cell(row, idx, "name", "facilityName"));
        m.put("facilityCode", CsvLineParser.cell(row, idx, "facilityCode", "code"));
        m.put("facilityType", CsvLineParser.cell(row, idx, "facilityType", "type"));
        m.put("province", CsvLineParser.cell(row, idx, "province"));
        m.put("district", CsvLineParser.cell(row, idx, "district"));
        return m;
    }

    private void writeJson(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist intake document: " + e.getMessage(), e);
        }
    }

    private <T> Optional<T> readJson(String key, TypeReference<T> type) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(raw, type));
        } catch (Exception e) {
            log.warn("Failed to read intake key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
