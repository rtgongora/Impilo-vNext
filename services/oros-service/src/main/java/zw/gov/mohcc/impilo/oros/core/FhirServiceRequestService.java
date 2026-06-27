package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

/**
 * FHIR ServiceRequest-inbound adapter: parse an external FHIR R4 {@code ServiceRequest} and hand it
 * to the shared {@link ExternalOrderIntakeService}. Patient identity comes from {@code subject}
 * (Patient/CPID or identifier value); production should cross-walk an external MRN → CPID via VITO
 * before this point.
 */
@Service
public class FhirServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(FhirServiceRequestService.class);

    private final ExternalOrderIntakeService intakeService;

    public FhirServiceRequestService(ExternalOrderIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @Transactional
    public OrderEntity ingest(JsonNode sr) {
        String code = extractCode(sr);
        ExternalOrderIntake input = new ExternalOrderIntake(
                extractCpid(sr), mapOrderType(sr), mapPriority(sr),
                code, extractCodeDisplay(sr, code), text(sr.path("note").path(0), "text"),
                extractExternalRef(sr));
        log.info("FHIR ServiceRequest parsed: externalRef={}, type={}", input.externalRef(), input.orderType());
        return intakeService.create(input);
    }

    // ── parsing helpers ──

    private String extractExternalRef(JsonNode sr) {
        JsonNode id0 = sr.path("identifier").path(0);
        String value = text(id0, "value");
        if (value == null) {
            return null;
        }
        String system = text(id0, "system");
        return system != null ? system + "|" + value : value;
    }

    private String extractCpid(JsonNode sr) {
        JsonNode subject = sr.path("subject");
        String ref = text(subject, "reference");
        if (ref != null && ref.startsWith("Patient/")) {
            return ref.substring("Patient/".length());
        }
        String idValue = text(subject.path("identifier"), "value");
        return idValue != null ? idValue : ref;
    }

    private OrderType mapOrderType(JsonNode sr) {
        String cat = text(sr.path("category").path(0).path("coding").path(0), "code");
        if (cat != null) {
            String c = cat.toLowerCase();
            if (c.contains("imag") || c.contains("rad") || c.equals("363679005")) {
                return OrderType.IMAGING;
            }
            if (c.contains("lab")) {
                return OrderType.LAB;
            }
        }
        return OrderType.LAB;
    }

    private OrderPriority mapPriority(JsonNode sr) {
        String p = text(sr, "priority");
        if (p == null) {
            return OrderPriority.ROUTINE;
        }
        return switch (p.toLowerCase()) {
            case "stat" -> OrderPriority.STAT;
            case "urgent" -> OrderPriority.URGENT;
            case "asap" -> OrderPriority.ASAP;
            default -> OrderPriority.ROUTINE;
        };
    }

    private String extractCode(JsonNode sr) {
        String code = text(sr.path("code").path("coding").path(0), "code");
        return code != null ? code : "UNSPECIFIED";
    }

    private String extractCodeDisplay(JsonNode sr, String fallback) {
        String d = text(sr.path("code").path("coding").path(0), "display");
        return d != null ? d : fallback;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
