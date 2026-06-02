package zw.gov.mohcc.impilo.experience.fhir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects Impilo patient envelopes to HL7 FHIR R4 {@code Patient} for interoperability
 * (HAPI FHIR / BUTANO SHR). MPI authority remains VITO; SHR write is a separate governed step.
 */
public final class FhirR4PatientMapper {

    private static final String IMPILO_STRUCTURE = "https://impilo.gov.zw/fhir/StructureDefinition";

    private FhirR4PatientMapper() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toPatientResource(Map<String, Object> patientEnvelope) {
        Map<String, Object> attrs = patientEnvelope.get("attributes") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        String id = stringVal(patientEnvelope.get("id"));
        String cpid = stringVal(attrs.get("cpid"));
        String given = stringVal(attrs.get("givenName"));
        String middle = stringVal(attrs.get("middleName"));
        String family = stringVal(attrs.get("familyName"));
        String dob = stringVal(attrs.get("dateOfBirth"));
        String sex = mapAdministrativeGender(stringVal(attrs.get("sex")));
        String phone = stringVal(attrs.get("phone"));
        String email = stringVal(attrs.get("email"));
        String nationalId = stringVal(attrs.get("nationalId"));
        String passport = stringVal(attrs.get("passportReference"));
        String addressLine = stringVal(attrs.get("addressLine1"));
        String city = stringVal(attrs.get("city"));
        String district = stringVal(attrs.get("district"));
        String province = stringVal(attrs.get("province"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        if (id != null) {
            resource.put("id", id);
        }

        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (cpid != null) {
            identifiers.add(identifier("https://impilo.gov.zw/cpid", cpid, "CPID"));
        }
        if (nationalId != null) {
            identifiers.add(identifier("http://zim.gov.zw/national-id", nationalId, "NN"));
        }
        if (passport != null) {
            identifiers.add(identifier("http://hl7.org/fhir/sid/passport-ZWE", passport, "PPN"));
        }
        if (!identifiers.isEmpty()) {
            resource.put("identifier", identifiers);
        }

        if (given != null || family != null) {
            Map<String, Object> humanName = new LinkedHashMap<>();
            humanName.put("use", "official");
            if (family != null) {
                humanName.put("family", family);
            }
            List<String> givenList = new ArrayList<>();
            if (given != null) {
                givenList.add(given);
            }
            if (middle != null && !middle.isBlank()) {
                givenList.add(middle);
            }
            if (!givenList.isEmpty()) {
                humanName.put("given", givenList);
            }
            resource.put("name", List.of(humanName));
        }

        if (sex != null) {
            resource.put("gender", sex);
        }
        if (dob != null && !dob.isBlank()) {
            resource.put("birthDate", dob);
        }

        List<Map<String, Object>> telecom = new ArrayList<>();
        if (phone != null) {
            telecom.add(telecom("phone", phone));
        }
        if (email != null) {
            telecom.add(telecom("email", email));
        }
        if (!telecom.isEmpty()) {
            resource.put("telecom", telecom);
        }

        if (addressLine != null || city != null || district != null || province != null) {
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("use", "home");
            if (addressLine != null) {
                address.put("line", List.of(addressLine));
            }
            if (city != null) {
                address.put("city", city);
            }
            if (district != null) {
                address.put("district", district);
            }
            if (province != null) {
                address.put("state", province);
            }
            String country = stringVal(attrs.get("country_alpha2"));
            if (country != null) {
                address.put("country", country);
            }
            resource.put("address", List.of(address));
        }

        List<Map<String, Object>> extensions = new ArrayList<>();
        putExtension(extensions, IMPILO_STRUCTURE + "/impilo-health-id", "valueString", stringVal(attrs.get("impiloHealthId")));
        putExtension(extensions, IMPILO_STRUCTURE + "/registry-sync-state", "valueString", stringVal(attrs.get("registrySyncState")));
        putExtension(extensions, IMPILO_STRUCTURE + "/assurance-level", "valueString", stringVal(attrs.get("assurance_level")));
        if (!extensions.isEmpty()) {
            resource.put("extension", extensions);
        }

        resource.put("meta", Map.of(
                "profile", List.of("http://hl7.org/fhir/StructureDefinition/Patient")));

        return resource;
    }

    private static Map<String, Object> identifier(String system, String value, String typeCode) {
        Map<String, Object> id = new LinkedHashMap<>();
        id.put("system", system);
        id.put("value", value);
        id.put("type", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v2-0203",
                "code", typeCode))));
        return id;
    }

    private static Map<String, Object> telecom(String system, String value) {
        return Map.of("system", system, "value", value, "use", "home");
    }

    private static void putExtension(List<Map<String, Object>> extensions, String url, String valueKey, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("url", url);
        ext.put(valueKey, value);
        extensions.add(ext);
    }

    private static String mapAdministrativeGender(String sex) {
        if (sex == null) {
            return "unknown";
        }
        return switch (sex.toLowerCase()) {
            case "male", "m" -> "male";
            case "female", "f" -> "female";
            case "other" -> "other";
            default -> "unknown";
        };
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }
}
