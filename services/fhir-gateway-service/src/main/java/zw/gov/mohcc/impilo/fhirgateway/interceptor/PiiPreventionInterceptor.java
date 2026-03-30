package zw.gov.mohcc.impilo.fhirgateway.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.fhirgateway.config.FhirGatewayProperties;

import java.util.List;

@Component
public class PiiPreventionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PiiPreventionInterceptor.class);

    public static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";

    private static final List<String> PATIENT_PII_FIELDS =
            List.of("name", "telecom", "address", "photo", "contact");

    private final ObjectMapper objectMapper;
    private final FhirGatewayProperties properties;

    public PiiPreventionInterceptor(ObjectMapper objectMapper, FhirGatewayProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void validatePayload(String jsonBody) {
        if (!properties.getPii().isEnforce()) {
            return;
        }
        if (jsonBody == null || jsonBody.isBlank()) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonBody);
        } catch (Exception e) {
            log.debug("PII check: could not parse body as JSON — skipping validation");
            return;
        }

        JsonNode resourceTypeNode = root.get("resourceType");
        if (resourceTypeNode == null || !"Patient".equals(resourceTypeNode.asText())) {
            return;
        }

        StringBuilder violations = new StringBuilder();

        for (String field : PATIENT_PII_FIELDS) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull()) {
                if (node.isArray() && !node.isEmpty()) {
                    violations.append("Patient.").append(field)
                            .append(" is not permitted in the SHR — stored in VITO. ");
                } else if (!node.isArray()) {
                    violations.append("Patient.").append(field)
                            .append(" is not permitted in the SHR — stored in VITO. ");
                }
            }
        }

        JsonNode identifiers = root.get("identifier");
        if (identifiers != null && identifiers.isArray()) {
            for (JsonNode identifier : identifiers) {
                JsonNode system = identifier.get("system");
                if (system == null || system.isNull() || !CPID_SYSTEM.equals(system.asText())) {
                    violations.append("Patient.identifier must use system '")
                            .append(CPID_SYSTEM)
                            .append("' — only CPID identifiers are allowed in the SHR. ");
                }
            }
        }

        if (!violations.isEmpty()) {
            String message = violations.toString().trim();
            log.warn("PII prevention: Patient resource rejected — {}", message);
            throw new PiiViolationException(
                    "PII violation in Patient resource: " + message +
                    " The BUTANO Shared Health Record is PII-free. Patient demographics " +
                    "must be stored in VITO (the identity service) and referenced by CPID only.");
        }

        log.debug("PII check passed — no PII fields detected in Patient resource");
    }
}
