package zw.gov.mohcc.impilo.experience.facility;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic facility names for preview/dev registries when TUSO numeric lookup is unavailable.
 */
public final class BffSeededFacilities {

    private static final Map<String, String> NAMES_BY_ID = buildNamesById();

    private BffSeededFacilities() {
    }

    public static String resolveName(String facilityRef) {
        if (facilityRef == null || facilityRef.isBlank()) {
            return null;
        }
        String key = facilityRef.trim();
        String direct = NAMES_BY_ID.get(key);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String lower = key.toLowerCase();
        for (Map.Entry<String, String> entry : NAMES_BY_ID.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) || entry.getKey().equalsIgnoreCase(lower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Map<String, String> buildNamesById() {
        Map<String, String> names = new LinkedHashMap<>();
        // experience-bff V2__seed_facilities.sql UUID registry
        names.put("a1b2c3d4-0001-4000-8000-000000000001", "Harare Central Hospital");
        names.put("a1b2c3d4-0002-4000-8000-000000000002", "Parirenyatwa Group of Hospitals");
        names.put("a1b2c3d4-0003-4000-8000-000000000003", "Chitungwiza Central Hospital");
        names.put("a1b2c3d4-0004-4000-8000-000000000004", "Mpilo Central Hospital");
        names.put("a1b2c3d4-0005-4000-8000-000000000005", "United Bulawayo Hospitals");
        names.put("a1b2c3d4-0006-4000-8000-000000000006", "Mutare Provincial Hospital");
        names.put("a1b2c3d4-0007-4000-8000-000000000007", "Gweru Provincial Hospital");
        names.put("a1b2c3d4-0008-4000-8000-000000000008", "Masvingo Provincial Hospital");
        names.put("a1b2c3d4-0009-4000-8000-000000000009", "Bindura Provincial Hospital");
        names.put("a1b2c3d4-0010-4000-8000-000000000010", "Chinhoyi Provincial Hospital");
        // FacilityController stub slugs (Maestro / stub mode)
        names.put("fac-hch", "Harare Central Hospital");
        names.put("fac-pgh", "Parirenyatwa Group of Hospitals");
        names.put("fac-uch", "United Bulawayo Hospitals");
        names.put("fac-mpc", "Mpilo Central Hospital");
        names.put("fac-chi", "Chitungwiza Central Hospital");
        return Map.copyOf(names);
    }
}
