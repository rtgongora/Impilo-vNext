package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.ZiboServiceClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TM-B6 pre-submission validation of a structured teleconsult response. Runs three governed checks
 * before the response is filed, and attaches the result so the provider sees issues at the point of
 * care:
 * <ol>
 *   <li><b>Terminology</b> — the coded diagnosis is validated against ZIBO ($validate-code); an
 *       unknown code in an unknown/unregistered system is a WARNING (ERROR only if the system is
 *       registered but the code is not).</li>
 *   <li><b>Prescribing authority</b> — if the response carries prescriptions, the responder's VARAPI
 *       provider standing must be ACTIVE (reuses the A2b authority axis); otherwise ERROR.</li>
 *   <li><b>Allergy conflict</b> — prescribed medications are cross-checked against the patient's
 *       recorded allergies. NOTE: the allergy SoR endpoint is not yet implemented (PCT /v1/allergies
 *       404s), so this check currently degrades to a WARNING ("allergies could not be verified")
 *       rather than a false all-clear — it activates automatically once the allergy store exists.</li>
 * </ol>
 *
 * <p>Shadow-then-enforce via {@code teleconsult.response-validation.mode} (OFF/SHADOW/ENFORCE,
 * default SHADOW). SHADOW records issues and always allows the response through; ENFORCE blocks
 * (422) when there is any ERROR. Every check is independently degradable — a downstream outage
 * yields a WARNING, never a hard failure of the whole submission.</p>
 */
@Service
public class TeleconsultResponseValidationService {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultResponseValidationService.class);

    public enum Mode { OFF, SHADOW, ENFORCE }

    private final ZiboServiceClient ziboClient;
    private final VarapiServiceClient varapiClient;
    private final PctServiceClient pctClient;
    private final ObjectMapper objectMapper;
    private final Mode mode;

    public TeleconsultResponseValidationService(ZiboServiceClient ziboClient,
                                                VarapiServiceClient varapiClient,
                                                PctServiceClient pctClient,
                                                ObjectMapper objectMapper,
                                                @Value("${teleconsult.response-validation.mode:SHADOW}") String mode) {
        this.ziboClient = ziboClient;
        this.varapiClient = varapiClient;
        this.pctClient = pctClient;
        this.objectMapper = objectMapper;
        Mode parsed;
        try { parsed = Mode.valueOf(mode.trim().toUpperCase()); } catch (Exception e) { parsed = Mode.SHADOW; }
        this.mode = parsed;
    }

    /** One validation issue: severity (ERROR/WARNING), a stable code, and a human message. */
    public record Issue(String severity, String code, String message) {}

    /** Outcome of a pre-submission validation pass. */
    public record Outcome(Mode mode, List<Issue> issues) {
        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        }
        /** In ENFORCE mode a response with any ERROR must be blocked before it is filed. */
        public boolean blocks() {
            return mode == Mode.ENFORCE && hasErrors();
        }
    }

    /**
     * Validate a structured response payload.
     *
     * @param body        the structured response (v1 spine + v2 sections)
     * @param actorId     the calling provider's actor id (fallback prescriber id)
     * @param patientCpid the subject; may be {@code null} (allergy check then records it could not run)
     */
    @SuppressWarnings("unchecked")
    public Outcome validate(Map<String, Object> body, String actorId, String patientCpid) {
        List<Issue> issues = new ArrayList<>();
        if (mode == Mode.OFF || body == null) {
            return new Outcome(mode, issues);
        }

        // 1. Terminology — coded diagnosis against ZIBO.
        Object cd = firstValue(body, "codedDiagnosis", "coded_diagnosis");
        if (cd instanceof Map<?, ?> coded) {
            String system = asString(coded.get("system"));
            String code = asString(coded.get("code"));
            if (notBlank(system) && notBlank(code)) {
                try {
                    JsonNode result = ziboClient.validateCoding(system, code, asString(coded.get("display")));
                    boolean valid = result != null && result.path("valid").asBoolean(false);
                    // ZIBO's ValidationResultDto carries no systemRegistered flag — an unregistered
                    // system surfaces as an issue with code "not-found" (OF-B3 fix: the old read of
                    // a nonexistent field made the unknown-system downgrade dead code).
                    boolean systemUnregistered = false;
                    if (result != null && result.path("issues").isArray()) {
                        for (JsonNode issue : result.path("issues")) {
                            if ("not-found".equals(issue.path("code").asText())) {
                                systemUnregistered = true;
                                break;
                            }
                        }
                    }
                    if (!valid) {
                        issues.add(systemUnregistered
                                ? new Issue("WARNING", "TERMINOLOGY_SYSTEM_UNREGISTERED",
                                        "Code system " + system + " is not registered in ZIBO — coding unverified.")
                                : new Issue("ERROR", "CODED_DIAGNOSIS_INVALID",
                                        "Coded diagnosis " + code + " is not valid in " + system + "."));
                    }
                } catch (Exception e) {
                    log.warn("ZIBO code validation unavailable: {}", e.getMessage());
                    issues.add(new Issue("WARNING", "TERMINOLOGY_UNVERIFIED",
                            "Coded diagnosis could not be validated (terminology service unavailable)."));
                }
            }
        }

        // Prescriptions drive both the authority and allergy checks.
        List<Map<String, Object>> prescriptions = new ArrayList<>();
        Object rx = firstValue(body, "prescriptions");
        if (rx instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) prescriptions.add((Map<String, Object>) m);
            }
        }

        // 2. Prescribing authority — responder must be an ACTIVE provider to prescribe.
        if (!prescriptions.isEmpty()) {
            String prescriberId = notBlank(asString(firstValue(body, "responderId", "responder_id")))
                    ? asString(firstValue(body, "responderId", "responder_id")) : actorId;
            if (notBlank(prescriberId)) {
                try {
                    JsonNode provider = varapiClient.getProvider(prescriberId);
                    String status = provider == null ? null : provider.path("status").asText(null);
                    if (!"ACTIVE".equalsIgnoreCase(status)) {
                        issues.add(new Issue("ERROR", "PRESCRIBER_NOT_AUTHORIZED",
                                "Prescriber " + prescriberId + " is not in good standing to prescribe (status="
                                        + status + ")."));
                    }
                } catch (Exception e) {
                    log.warn("VARAPI prescribing-authority check unavailable: {}", e.getMessage());
                    issues.add(new Issue("WARNING", "PRESCRIBING_AUTHORITY_UNVERIFIED",
                            "Prescribing authority could not be verified."));
                }
            }
        }

        // 3. Allergy conflict — prescribed meds vs the patient's recorded allergies.
        if (!prescriptions.isEmpty()) {
            if (!notBlank(patientCpid)) {
                issues.add(new Issue("WARNING", "ALLERGY_CHECK_NO_SUBJECT",
                        "Allergy conflict could not be checked (no patient reference on the response)."));
            } else {
                try {
                    JsonNode allergies = pctClient.listAllergies(patientCpid);
                    List<AllergyRef> refs = allergyRefs(allergies);
                    for (Map<String, Object> p : prescriptions) {
                        String med = asString(p.get("medication"));
                        String medCode = asString(p.get("code"));
                        for (AllergyRef ref : refs) {
                            // Code-aware first (OF-B3: the PCT SoR is coded), then name substring.
                            boolean codeHit = notBlank(medCode) && notBlank(ref.code())
                                    && medCode.equalsIgnoreCase(ref.code());
                            boolean nameHit = notBlank(med) && notBlank(ref.display()) && matches(med, ref.display());
                            if (codeHit || nameHit) {
                                issues.add(new Issue("ERROR", "ALLERGY_CONFLICT",
                                        "Patient has a recorded allergy to " + ref.display()
                                                + (codeHit ? " [" + ref.code() + "]" : "")
                                                + " — conflicts with prescribed " + (notBlank(med) ? med : medCode) + "."));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Allergy SoR endpoint not yet implemented → honest WARNING, never a false all-clear.
                    log.warn("Allergy conflict check could not run: {}", e.getMessage());
                    issues.add(new Issue("WARNING", "ALLERGIES_UNVERIFIED",
                            "Patient allergies could not be checked — verify manually before prescribing."));
                }
            }
        }

        Outcome outcome = new Outcome(mode, issues);
        if (!issues.isEmpty()) {
            log.info("Teleconsult response validation [{}]: {} issue(s), errors={}",
                    mode, issues.size(), outcome.hasErrors());
        }
        return outcome;
    }

    /** Render the outcome as a JSON block to attach to the response / return in a 422 body. */
    public ObjectNode toJson(Outcome outcome) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("mode", outcome.mode().name());
        node.put("passed", !outcome.hasErrors());
        ArrayNode arr = node.putArray("issues");
        for (Issue i : outcome.issues()) {
            arr.addObject().put("severity", i.severity()).put("code", i.code()).put("message", i.message());
        }
        return node;
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    /** A recorded allergy reference: display name + coded allergen when the SoR carries one. */
    record AllergyRef(String display, String code) {}

    private List<AllergyRef> allergyRefs(JsonNode allergies) {
        List<AllergyRef> out = new ArrayList<>();
        if (allergies == null) return out;
        JsonNode arr = allergies.isArray() ? allergies : allergies.path("items");
        if (arr.isArray()) {
            for (JsonNode a : arr) {
                String display = null;
                for (String f : new String[] {"substance", "allergen", "name", "display"}) {
                    String v = a.path(f).asText(null);
                    if (v != null && !v.isBlank()) { display = v; break; }
                }
                String code = a.path("allergen_code").asText(null);
                if (code == null || code.isBlank()) code = a.path("code").asText(null);
                if (display != null || (code != null && !code.isBlank())) {
                    out.add(new AllergyRef(display, code));
                }
            }
        }
        return out;
    }

    private boolean matches(String medication, String substance) {
        String m = medication.toLowerCase();
        String s = substance.toLowerCase();
        return m.contains(s) || s.contains(m);
    }

    private Object firstValue(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
