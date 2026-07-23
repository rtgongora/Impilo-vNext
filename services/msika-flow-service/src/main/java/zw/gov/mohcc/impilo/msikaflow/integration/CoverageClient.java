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
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("coverageId", coverageId.toString());
            body.put("benefitCode", benefitCode);
            body.put("standardCharge", standardCharge);
            ResponseEntity<String> response = restClient.post()
                    .uri("/internal/v1/coverage/liability-estimates")
                    .headers(h -> copyTrustHeaders(inbound, h))
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
                    node.path("benefitCode").asText(benefitCode),
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
            body.put("authType", "MARKETPLACE_FULFILMENT");
            body.put("lines", lines);

            ResponseEntity<String> created = restClient.post()
                    .uri("/internal/v1/coverage/authorisations")
                    .headers(h -> copyTrustHeaders(inbound, h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
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
                    .headers(h -> copyTrustHeaders(inbound, h))
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
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
