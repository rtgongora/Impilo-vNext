package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Telemedicine operating-model registry.
 *
 * <p>The virtual-hospital directory is now TUSO-backed (the
 * {@code tuso.virtual_service_*} registry is the system of record). Resolution
 * order, honest at every step:</p>
 * <ol>
 *   <li><b>TUSO</b> — live rows from {@code /v1/internal/virtual-services},
 *       mapped to the operating-model document shape ({@code source=TUSO});</li>
 *   <li><b>cached last-good</b> — the last successful TUSO read when TUSO is
 *       unreachable ({@code source=TUSO}, {@code registryDegraded=true});</li>
 *   <li><b>classpath fallback</b> — the versioned doctrine-as-data resource
 *       (canonical copy: {@code contracts/telemedicine/virtual-hospitals.json})
 *       when TUSO has never answered or is unpopulated
 *       ({@code source=STATIC_FALLBACK}).</li>
 * </ol>
 *
 * <p>Every payload carries {@code substrateStatus} honestly; per-queue LIVE
 * status is composed downstream from PCT materialisation state, never claimed
 * here. The clinical-group taxonomy remains classpath doctrine (HO-5
 * fail-closed).</p>
 */
@Component
public class TelemedicineOperatingModelRegistry {

    public static final String SOURCE_TUSO = "TUSO";
    public static final String SOURCE_STATIC_FALLBACK = "STATIC_FALLBACK";

    private static final Logger log = LoggerFactory.getLogger(TelemedicineOperatingModelRegistry.class);
    private static final String RESOURCE_ROOT = "/telemedicine/operating-model/";

    /** Resolved directory + provenance. */
    public record Directory(List<Map<String, Object>> virtualHospitals, String source, boolean registryDegraded) {

        public Optional<Map<String, Object>> byId(String id) {
            return virtualHospitals.stream().filter(vh -> id != null && id.equals(vh.get("id"))).findFirst();
        }
    }

    private final TusoServiceClient tusoServiceClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> staticVirtualHospitals;
    private final Map<String, Map<String, Object>> staticVirtualHospitalsById;
    private final Map<String, Object> clinicalGroups;

    /** Last successful TUSO read (served, flagged degraded, while TUSO is down). */
    private volatile List<Map<String, Object>> lastGoodTusoDirectory;

    @Autowired
    public TelemedicineOperatingModelRegistry(TusoServiceClient tusoServiceClient) {
        this.tusoServiceClient = tusoServiceClient;
        Map<String, Object> hospitalsDoc = readResource(mapper, "virtual-hospitals.json");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hospitals = (List<Map<String, Object>>) hospitalsDoc.get("virtualHospitals");
        if (hospitals == null || hospitals.isEmpty()) {
            throw new IllegalStateException("virtual-hospitals.json has no virtualHospitals entries");
        }
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> hospital : hospitals) {
            String id = (String) hospital.get("id");
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IllegalStateException("virtual-hospitals.json: missing or duplicate id: " + id);
            }
        }
        this.staticVirtualHospitals = List.copyOf(hospitals);
        this.staticVirtualHospitalsById = hospitals.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(h -> (String) h.get("id"), h -> h));

        Map<String, Object> groupsDoc = readResource(mapper, "clinical-groups.json");
        if (!(groupsDoc.get("groupTypes") instanceof List<?> groupTypes) || groupTypes.isEmpty()) {
            throw new IllegalStateException("clinical-groups.json has no groupTypes entries");
        }
        this.clinicalGroups = Map.copyOf(groupsDoc);
    }

    /** Fallback-only construction (no TUSO client) — static doctrine document. */
    public TelemedicineOperatingModelRegistry() {
        this((TusoServiceClient) null);
    }

    private static Map<String, Object> readResource(ObjectMapper mapper, String name) {
        try (InputStream in = TelemedicineOperatingModelRegistry.class
                .getResourceAsStream(RESOURCE_ROOT + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE_ROOT + name);
            }
            return mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE_ROOT + name, e);
        }
    }

    /**
     * Resolve the virtual-hospital directory: TUSO → cached-last-good →
     * classpath fallback. Never throws on TUSO failure.
     */
    public Directory virtualHospitalDirectory() {
        if (tusoServiceClient == null) {
            return new Directory(staticVirtualHospitals, SOURCE_STATIC_FALLBACK, false);
        }
        try {
            JsonNode rows = tusoServiceClient.listVirtualServices();
            if (rows != null && rows.isArray() && !rows.isEmpty()) {
                List<Map<String, Object>> mapped = new ArrayList<>();
                for (JsonNode row : rows) {
                    mapped.add(mapTusoRow(row));
                }
                List<Map<String, Object>> directory = List.copyOf(mapped);
                lastGoodTusoDirectory = directory;
                return new Directory(directory, SOURCE_TUSO, false);
            }
            // TUSO reachable but unpopulated (e.g. registry not seeded for this
            // tenant) — serve the doctrine document; that is not a degradation.
            return new Directory(staticVirtualHospitals, SOURCE_STATIC_FALLBACK, false);
        } catch (Exception e) {
            List<Map<String, Object>> cached = lastGoodTusoDirectory;
            if (cached != null) {
                log.warn("TUSO virtual-service registry unreachable ({}); serving cached last-good directory",
                        e.getMessage());
                return new Directory(cached, SOURCE_TUSO, true);
            }
            log.warn("TUSO virtual-service registry unreachable ({}); serving static fallback", e.getMessage());
            return new Directory(staticVirtualHospitals, SOURCE_STATIC_FALLBACK, true);
        }
    }

    /**
     * Map a TUSO virtual-service row to the operating-model document shape the
     * experience shell renders. Additive fields (lifecycleStatus, routingSeams,
     * queueDefs) surface governance state; substrateStatus is computed, never
     * stored: ACTIVE → OPERATIONAL, else active seam → ROUTABLE_VIA_EXISTING_SEAMS,
     * else CONFIGURED_AWAITING_SUBSTRATE.
     */
    private Map<String, Object> mapTusoRow(JsonNode row) {
        Map<String, Object> vh = new LinkedHashMap<>();
        String vsUid = text(row, "vsUid") != null ? text(row, "vsUid") : text(row, "id");
        vh.put("id", vsUid);
        vh.put("name", text(row, "name"));
        vh.put("level", text(row, "level"));
        vh.put("operatingModel", text(row, "operatingModel"));
        vh.put("operatingAuthority", text(row, "operatingAuthority"));
        vh.put("regulatoryStatus", text(row, "regulatoryStatus"));
        vh.put("purpose", text(row, "purpose"));
        vh.put("catchment", text(row, "catchment"));
        vh.put("serviceLines", toList(row.get("serviceLines")));
        vh.put("providerPoolCadres", toList(row.get("providerPoolCadres")));
        vh.put("linkedFacilityRoles", toList(row.get("linkedFacilityRoles")));
        vh.put("crossBorderParticipation", row.path("crossBorderParticipation").asBoolean(false));
        vh.put("allowedCrossBorderPrivileges", toList(row.get("allowedCrossBorderPrivileges")));
        vh.put("billingModels", toList(row.get("billingModels")));
        vh.put("sessionModeIds", toList(row.get("sessionModeIds")));

        List<Map<String, Object>> queues = new ArrayList<>();
        List<Map<String, Object>> queueDefs = new ArrayList<>();
        for (JsonNode def : iterable(row.get("queueDefs"))) {
            Map<String, Object> queue = new LinkedHashMap<>();
            queue.put("id", text(def, "queueKey"));
            queue.put("name", text(def, "name"));
            // Honest default: LIVE is only asserted by PCT-backed composition.
            queue.put("status", "AWAITING_BACKEND");
            queues.add(queue);
            queueDefs.add(mapper.convertValue(def, new TypeReference<Map<String, Object>>() {}));
        }
        vh.put("queues", queues);
        vh.put("queueDefs", queueDefs);

        List<Map<String, Object>> allSeams = new ArrayList<>();
        List<Map<String, Object>> activeSeams = new ArrayList<>();
        for (JsonNode seam : iterable(row.get("routingSeams"))) {
            Map<String, Object> mappedSeam = mapper.convertValue(seam, new TypeReference<Map<String, Object>>() {});
            allSeams.add(mappedSeam);
            if (seam.path("active").asBoolean(false)) {
                Map<String, Object> publicSeam = new LinkedHashMap<>();
                publicSeam.put("routingType", text(seam, "routingType"));
                publicSeam.put("targetRef", text(seam, "targetRef"));
                publicSeam.put("note", text(seam, "note"));
                activeSeams.add(publicSeam);
            }
        }
        vh.put("routingSeams", allSeams);
        vh.put("currentRoutingSeams", activeSeams);

        String lifecycle = text(row, "lifecycleStatus");
        vh.put("lifecycleStatus", lifecycle);
        vh.put("activatedAt", text(row, "activatedAt"));
        vh.put("activatedBy", text(row, "activatedBy"));
        if (text(row, "provinceCode") != null) vh.put("provinceCode", text(row, "provinceCode"));

        String substrateStatus;
        if ("ACTIVE".equals(lifecycle)) {
            substrateStatus = "OPERATIONAL";
        } else if (!activeSeams.isEmpty()) {
            substrateStatus = "ROUTABLE_VIA_EXISTING_SEAMS";
        } else {
            substrateStatus = "CONFIGURED_AWAITING_SUBSTRATE";
        }
        vh.put("substrateStatus", substrateStatus);
        return vh;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private List<String> toList(JsonNode node) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : iterable(node)) {
            if (!item.isNull()) out.add(item.asText());
        }
        return out;
    }

    /** Full governed virtual-hospital directory — STATIC doctrine document (fallback resource). */
    public List<Map<String, Object>> virtualHospitals() {
        return staticVirtualHospitals;
    }

    public Optional<Map<String, Object>> virtualHospital(String id) {
        return Optional.ofNullable(staticVirtualHospitalsById.get(id));
    }

    /** Clinical-group taxonomy + the fail-closed creation governance (HO-5). */
    public Map<String, Object> clinicalGroups() {
        return clinicalGroups;
    }
}
