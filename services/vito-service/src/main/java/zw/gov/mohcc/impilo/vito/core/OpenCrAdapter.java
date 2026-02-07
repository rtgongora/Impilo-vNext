package zw.gov.mohcc.impilo.vito.core;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;

/**
 * Mode A: OpenCR FHIR Client Adapter.
 *
 * Syncs/links identities with an external OpenCR instance via HL7 FHIR
 * Implementation Guides. Uses FHIR Patient/$match operation for
 * probabilistic matching against the external registry.
 *
 * Active only when vito.registry-mode=OPENCR.
 */
@Component
public class OpenCrAdapter {

    private final VitoProperties properties;
    private final RestTemplate restTemplate;

    public OpenCrAdapter(VitoProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if OpenCR mode is active.
     */
    public boolean isEnabled() {
        return "OPENCR".equalsIgnoreCase(properties.getRegistryMode());
    }

    /**
     * Execute FHIR $match against OpenCR.
     *
     * @param givenName   patient given name
     * @param familyName  patient family name
     * @param dateOfBirth patient DOB (YYYY-MM-DD)
     * @param sex         patient sex
     * @return FHIR Bundle JSON response from OpenCR, or null if not enabled
     */
    public String fhirMatch(String givenName, String familyName,
                             String dateOfBirth, String sex) {
        if (!isEnabled()) return null;

        String fhirPatient = String.format("""
                {
                  "resourceType": "Parameters",
                  "parameter": [{
                    "name": "resource",
                    "resource": {
                      "resourceType": "Patient",
                      "name": [{"given": ["%s"], "family": "%s"}],
                      "birthDate": "%s",
                      "gender": "%s"
                    }
                  }]
                }""", givenName, familyName, dateOfBirth, mapSexToFhirGender(sex));

        try {
            String url = properties.getOpencr().getBaseUrl() + "/Patient/$match";
            return restTemplate.postForObject(url, fhirPatient, String.class);
        } catch (Exception e) {
            // OpenCR unavailable — degrade gracefully to standalone mode
            return null;
        }
    }

    private String mapSexToFhirGender(String sex) {
        if (sex == null) return "unknown";
        return switch (sex.toUpperCase()) {
            case "MALE", "M" -> "male";
            case "FEMALE", "F" -> "female";
            default -> "unknown";
        };
    }
}
