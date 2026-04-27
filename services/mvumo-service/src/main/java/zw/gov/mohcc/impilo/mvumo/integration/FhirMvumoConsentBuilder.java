package zw.gov.mohcc.impilo.mvumo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Builds a minimal valid FHIR R4 Consent JSON for {@code tshepo-consent-service} (HAPI parser + mapper validation).
 */
public final class FhirMvumoConsentBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private FhirMvumoConsentBuilder() {}

    public static String buildJson(
            String patientRef,
            String grantorRef,
            String mvumoRequestId,
            String consentType,
            Instant notBefore) {
        Objects.requireNonNull(patientRef, "patientRef");
        String grantor = grantorRef != null && !grantorRef.isBlank() ? grantorRef : patientRef;
        Instant t = notBefore != null ? notBefore : Instant.now();
        String when = ISO.format(t);
        String typeLabel = consentType != null && !consentType.isBlank() ? consentType : "mvumo-consent";

        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "Consent");
        root.put("status", "active");
        // scope
        ObjectNode scope = root.putObject("scope");
        ArrayNode sCoding = scope.putArray("coding");
        ObjectNode s1 = sCoding.addObject();
        s1.put("system", "http://terminology.hl7.org/CodeSystem/consentscope");
        s1.put("code", "patient-privacy");
        // category
        ArrayNode cat = root.putArray("category");
        ObjectNode c0 = cat.addObject();
        ArrayNode cCoding = c0.putArray("coding");
        ObjectNode c1 = cCoding.addObject();
        c1.put("system", "http://terminology.hl7.org/CodeSystem/consentcategorycodes");
        c1.put("code", "npp");
        root.putObject("patient").put("reference", patientRef);
        ArrayNode perf = root.putArray("performer");
        perf.addObject().put("reference", grantor);
        root.put("dateTime", when);
        // extension: mvumo id
        ArrayNode ext = root.putArray("extension");
        ObjectNode ex0 = ext.addObject();
        ex0.put("url", "https://impilo.gov.zw/fhir/StructureDefinition/mvumo-consent-request");
        ex0.put("valueString", mvumoRequestId != null ? mvumoRequestId : "unknown");
        root.putObject("policyRule").put("text", typeLabel);
        // provision
        ObjectNode prov = root.putObject("provision");
        prov.put("type", "permit");
        prov.putObject("period").put("start", when);

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build FHIR Consent JSON", e);
        }
    }
}
