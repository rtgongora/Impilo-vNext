package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Telemedicine operating-model registry (doctrine-as-data, no tenant data).
 *
 * Serves the governed virtual-hospital directory and the clinical-group
 * taxonomy from versioned classpath resources — the same documents the
 * experience shell renders on /work/telemedicine/virtual-hospitals and
 * /work/telemedicine/groups. The resources are the canonical serialization of
 * the seed spec in ui/one-ui-shell/src/lib/telemedicine (a drift test on the
 * UI side pins the two together). Every virtual hospital carries an honest
 * substrateStatus; nothing here claims runtime capability (queues, rosters,
 * live metrics) that the pending HO-2 substrate does not provide.
 */
@Component
public class TelemedicineOperatingModelRegistry {

    private static final String RESOURCE_ROOT = "/telemedicine/operating-model/";

    private final List<Map<String, Object>> virtualHospitals;
    private final Map<String, Map<String, Object>> virtualHospitalsById;
    private final Map<String, Object> clinicalGroups;

    public TelemedicineOperatingModelRegistry() {
        ObjectMapper mapper = new ObjectMapper();
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
        this.virtualHospitals = List.copyOf(hospitals);
        this.virtualHospitalsById = hospitals.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(h -> (String) h.get("id"), h -> h));

        Map<String, Object> groupsDoc = readResource(mapper, "clinical-groups.json");
        if (!(groupsDoc.get("groupTypes") instanceof List<?> groupTypes) || groupTypes.isEmpty()) {
            throw new IllegalStateException("clinical-groups.json has no groupTypes entries");
        }
        this.clinicalGroups = Map.copyOf(groupsDoc);
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

    /** Full governed virtual-hospital directory (strategic + provincial). */
    public List<Map<String, Object>> virtualHospitals() {
        return virtualHospitals;
    }

    public Optional<Map<String, Object>> virtualHospital(String id) {
        return Optional.ofNullable(virtualHospitalsById.get(id));
    }

    /** Clinical-group taxonomy + the fail-closed creation governance (HO-5). */
    public Map<String, Object> clinicalGroups() {
        return clinicalGroups;
    }
}
