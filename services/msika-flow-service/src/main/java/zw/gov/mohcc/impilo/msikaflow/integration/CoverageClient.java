package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ruvimbo (coverage-service) client — OF-B8/OF-B9 seams.
 *
 * <p>Two live engines are wired, none re-invented (§10.1 ownership boundary):</p>
 * <ul>
 *   <li><b>Liability</b> — {@code POST /internal/v1/coverage/liability-estimates}
 *       (the LIVE {@code cv_liability_estimates} engine): eligibility→benefit→
 *       estimated patient liability for a COSTA standard charge. Every figure is
 *       an ESTIMATE by §10.5 — never presented as final.</li>
 *   <li><b>Prior authorisation</b> — the LIVE 14-status {@code cv_authorisations}
 *       machine: lookup by member, and a minimum-necessary create+submit
 *       (coded benefit lines only — never clinical narrative, §10.6).</li>
 * </ul>
 *
 * <p>Degradation contract (§10.4): {@code UNREACHABLE} on transport failure —
 * the caller marks liability {@code UNVERIFIED} and commitment step 7 fails
 * closed for payer-covered flows; {@code REFUSED} on a coverage-side 4xx
 * (coverage/plan not resolvable) — a hard not-covered answer, also fail-closed
 * but distinguishable on the display surface.</p>
 */
@Service
public class CoverageClient {

    private static final Logger log = LoggerFactory.getLogger(CoverageClient.class);

    public enum Outcome { OK, REFUSED, UNREACHABLE }

    /** One Ruvimbo liability estimate (a {@code cv_liability_estimates} row). */
    public record LiabilityEstimate(UUID estimateId, String memberCpid, String benefitCode,
                                    BigDecimal standardCharge, BigDecimal payerEstimate,
                                    BigDecimal patientResponsibility, String currency,
                                    boolean requiresAuthorisation, String assumptions) {}

    public record EstimateResult(Outcome outcome, LiabilityEstimate estimate) {
        public static EstimateResult unreachable() { return new EstimateResult(Outcome.UNREACHABLE, null); }
        public static EstimateResult refused() { return new EstimateResult(Outcome.REFUSED, null); }
    }

    /** A member authorisation summary (LIVE 14-status machine, §9.5). */
    public record Authorisation(UUID id, String status, List<String> benefitCodes) {}

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CoverageClient(ObjectMapper objectMapper,
                          @Value("${msika-flow.integration.coverage-url:http://localhost:8140}") String coverageBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(coverageBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /internal/v1/coverage/liability-estimates — the LIVE engine computes
     * allowed/payer/copay/coinsurance/patient from the plan's benefit definition
     * for the supplied COSTA standard charge.
     */
    public EstimateResult estimateLiability(UUID coverageId, String benefitCode,
                                            BigDecimal standardCharge, HttpServletRequest inbound) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coverageId", coverageId.toString());
        body.put("benefitCode", benefitCode);
        body.put("standardCharge", standardCharge);
        return postEstimate(coverageId, body, benefitCode,
                liabilityIdempotencyKey(coverageId, benefitCode, standardCharge), inbound);
    }

    /**
     * OF-B8 medication path — the line is ATC-coded against the ZIBO national
     * registry, so the ATC code goes over the seam as {@code medicationCode}
     * and Ruvimbo resolves the plan benefit THROUGH its payer formulary
     * ({@code cv_formulary}): the returned {@code benefitCode} is the MAPPED
     * plan benefit (or the honest non-covered posture when the medication has
     * no active/covered listing). This replaces the documented interim where
     * the raw item code was passed as the benefit code.
     */
    public EstimateResult estimateMedicationLiability(UUID coverageId, String medicationCode,
                                                      String codingSystem, BigDecimal standardCharge,
                                                      HttpServletRequest inbound) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coverageId", coverageId.toString());
        body.put("medicationCode", medicationCode);
        if (codingSystem != null && !codingSystem.isBlank()) {
            body.put("codingSystem", codingSystem);
        }
        body.put("standardCharge", standardCharge);
        return postEstimate(coverageId, body, medicationCode,
                medicationLiabilityIdempotencyKey(coverageId, medicationCode, codingSystem, standardCharge),
                inbound);
    }

    private EstimateResult postEstimate(UUID coverageId, Map<String, Object> body,
                                        String fallbackCode, String idempotencyKey,
                                        HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/internal/v1/coverage/liability-estimates")
                    .headers(h -> {
                        copyTrustHeaders(inbound, h);
                        // M3 BUG-4: coverage's v1.1 IdempotencyFilter hard-requires
                        // Idempotency-Key on POST /internal/v1/** — without it every
                        // liability call 400'd, the 4xx was misread as REFUSED and
                        // every payer-covered offer misreported NOT_COVERED.
                        // Deterministic per logical call: a retry of the same
                        // estimate replays; a different charge is a new key.
                        setIdempotencyKey(h, idempotencyKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Ruvimbo liability estimate failed: HTTP {}", response.getStatusCode());
                return EstimateResult.unreachable();
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) {
                log.warn("Ruvimbo liability estimate response missing id");
                return EstimateResult.unreachable();
            }
            return new EstimateResult(Outcome.OK, new LiabilityEstimate(
                    UUID.fromString(id),
                    node.path("memberCpid").asText(null),
                    node.path("benefitCode").asText(fallbackCode),
                    decimal(node, "standardCharge"),
                    decimal(node, "payerEstimate"),
                    decimal(node, "patientResponsibility"),
                    node.path("currency").asText("USD"),
                    node.path("requiresAuthorisation").asBoolean(false),
                    node.path("assumptions").asText(null)));
        } catch (RestClientResponseException e) {
            // 4xx: coverage/plan not resolvable — a hard REFUSED, not a transport gap.
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("Ruvimbo liability estimate refused for coverage {}: HTTP {}",
                        coverageId, e.getStatusCode());
                return EstimateResult.refused();
            }
            log.warn("Ruvimbo liability estimate failed: HTTP {}", e.getStatusCode());
            return EstimateResult.unreachable();
        } catch (Exception e) {
            log.warn("Ruvimbo liability estimate failed: {}", e.getMessage());
            return EstimateResult.unreachable();
        }
    }

    /**
     * GET /internal/v1/coverage/authorisations?member_cpid= — PA lookup against
     * the LIVE machine. Empty on transport failure (caller treats PA state
     * UNVERIFIED → fail closed for conditional flows).
     */
    public Optional<List<Authorisation>> findAuthorisations(String memberCpid, HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/v1/coverage/authorisations")
                            .queryParam("member_cpid", memberCpid).build())
                    .headers(h -> copyTrustHeaders(inbound, h))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Ruvimbo authorisation lookup failed: HTTP {}", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode arr = objectMapper.readTree(response.getBody());
            if (!arr.isArray()) {
                return Optional.empty();
            }
            List<Authorisation> out = new ArrayList<>();
            for (JsonNode a : arr) {
                String id = a.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                List<String> benefitCodes = new ArrayList<>();
                for (JsonNode line : a.path("lines")) {
                    String code = line.path("benefitCode").asText(null);
                    if (code != null && !code.isBlank()) {
                        benefitCodes.add(code);
                    }
                }
                out.add(new Authorisation(UUID.fromString(id), a.path("status").asText(null), benefitCodes));
            }
            return Optional.of(out);
        } catch (Exception e) {
            log.warn("Ruvimbo authorisation lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * §8.7.5 steps 1–3: create a DRAFT authorisation with the minimum-necessary
     * payload (coded benefit lines + estimated amounts ONLY — no diagnosis text,
     * no transcript, §10.6) and submit it (DRAFT → SUBMITTED). Returns the
     * authorisation id, or empty on any failure — the caller records PA state
     * PENDING/UNVERIFIED honestly, never a fabricated approval.
     */
    public Optional<UUID> submitAuthorisation(UUID coverageId, List<String> benefitCodes,
                                              Map<String, BigDecimal> estimatedAmounts,
                                              HttpServletRequest inbound) {
        try {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (String code : benefitCodes) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("benefitCode", code);
                BigDecimal amount = estimatedAmounts != null ? estimatedAmounts.get(code) : null;
                if (amount != null) {
                    line.put("estimatedAmount", amount);
                }
                lines.add(line);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("coverageId", coverageId.toString());
            // M3 fix-run finding: coverage OWNS the auth-type vocabulary
            // (AuthorisationService.AUTH_TYPES) and refused the invented
            // "MARKETPLACE_FULFILMENT" with 400 Unknown auth_type — the §8.7.5
            // minimum-necessary auto-submit never landed a row. The flow is a
            // PRIOR authorisation; the coded benefit lines carry the specifics.
            body.put("authType", "PRIOR");
            body.put("lines", lines);

            // M3 BUG-4 (same family as liability): both PA POSTs hit the v1.1
            // idempotency filter — deterministic keys per logical submission.
            // The key is derived from the EXACT serialized body (fix-run 2
            // finding: a key over codes+amounts only collided with 409
            // IDENTITY_CONFLICT when any other body field changed) — same
            // bytes replay, different bytes are a new logical call.
            String bodyJson = objectMapper.writeValueAsString(body);
            String createKey = paSubmissionIdempotencyKey(coverageId, bodyJson);
            ResponseEntity<String> created = restClient.post()
                    .uri("/internal/v1/coverage/authorisations")
                    .headers(h -> {
                        copyTrustHeaders(inbound, h);
                        setIdempotencyKey(h, createKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyJson)
                    .retrieve()
                    .toEntity(String.class);
            if (!created.getStatusCode().is2xxSuccessful() || created.getBody() == null) {
                log.warn("Ruvimbo authorisation create failed: HTTP {}", created.getStatusCode());
                return Optional.empty();
            }
            String id = objectMapper.readTree(created.getBody()).path("id").asText(null);
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            ResponseEntity<String> submitted = restClient.post()
                    .uri("/internal/v1/coverage/authorisations/{id}/submit", id)
                    .headers(h -> {
                        copyTrustHeaders(inbound, h);
                        setIdempotencyKey(h, "msika-flow-pa-submit:" + id);
                    })
                    .retrieve()
                    .toEntity(String.class);
            if (!submitted.getStatusCode().is2xxSuccessful()) {
                log.warn("Ruvimbo authorisation submit failed for {}: HTTP {}", id, submitted.getStatusCode());
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(id));
        } catch (Exception e) {
            log.warn("Ruvimbo authorisation submit failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isNumber() || v.isTextual()) ? new BigDecimal(v.asText()) : null;
    }

    /**
     * Deterministic idempotency key for one logical liability estimate:
     * same coverage/benefit/charge → same key (the filter replays the stored
     * estimate on a retry — same body, same hash); a changed charge is a NEW
     * logical call and gets a new key, never a 409 IDENTITY_CONFLICT.
     * Package-visible for the contract unit test.
     */
    static String liabilityIdempotencyKey(UUID coverageId, String benefitCode, BigDecimal standardCharge) {
        return "msika-flow-liability:" + coverageId + ":" + benefitCode + ":"
                + (standardCharge != null ? standardCharge.stripTrailingZeros().toPlainString() : "0");
    }

    /**
     * OF-B8 — deterministic key for one logical MEDICATION estimate. Distinct
     * prefix + the coding system keep it disjoint from the benefit-code key
     * family (same code string through both paths must never collide into a
     * 409 IDENTITY_CONFLICT replay of the other body shape).
     * Package-visible for the contract unit test.
     */
    static String medicationLiabilityIdempotencyKey(UUID coverageId, String medicationCode,
                                                    String codingSystem, BigDecimal standardCharge) {
        return "msika-flow-liability-med:" + coverageId + ":" + medicationCode + ":"
                + (codingSystem != null && !codingSystem.isBlank() ? codingSystem : "http://www.whocc.no/atc")
                + ":" + (standardCharge != null ? standardCharge.stripTrailingZeros().toPlainString() : "0");
    }

    /**
     * Deterministic key for one logical PA submission: SHA-256 over the exact
     * request body — identical bytes replay through the filter; ANY body
     * change (codes, amounts, authType, …) is a new logical call and can
     * never 409 IDENTITY_CONFLICT against an older stored submission.
     */
    static String paSubmissionIdempotencyKey(UUID coverageId, String bodyJson) {
        return "msika-flow-pa:" + coverageId + ":" + sha256Hex(bodyJson);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void setIdempotencyKey(HttpHeaders target, String key) {
        target.set("Idempotency-Key", key);
        target.set("X-Idempotency-Key", key);
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target) {
        if (inbound != null) {
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
            copyIfPresent(inbound, target, "Authorization");
        }
        String pod = inbound != null ? inbound.getHeader("X-Pod-ID") : null;
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        // Coverage endpoints are /internal/v1/** — the V11HeaderFilter hard-requires
        // X-Correlation-ID; synthesize one when the inbound hop carried none.
        if (!target.containsKey("X-Correlation-ID")) {
            target.add("X-Correlation-ID", java.util.UUID.randomUUID().toString());
        }
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
