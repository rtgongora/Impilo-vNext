package zw.gov.mohcc.impilo.msikaflow.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 BUG-10 + BUG-4 — the v1.1 outbound header contract for service-originated
 * hops (no inbound servlet request) and the deterministic idempotency keys on
 * the coverage seam.
 *
 * <p>BUG-10 live evidence: NhumeClient's inbound==null branch omitted
 * X-Correlation-ID, so Nhume's V11HeaderFilter 400'd every payment-resumed /
 * retry-sweep dispatch (MISSING_REQUIRED_HEADER) — DELIVERY commitments that
 * went AWAITING_PAYMENT→PAID were permanently undeliverable.</p>
 *
 * <p>BUG-4 live evidence: CoverageClient sent no Idempotency-Key, coverage's
 * IdempotencyFilter 400'd every liability POST, the 4xx was misread as REFUSED
 * and every payer-covered offer misreported NOT_COVERED (PA unreachable).</p>
 */
class ServiceOriginatedHeaderContractTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    @DisplayName("BUG-10: service-originated Nhume dispatch carries the FULL v1.1 hard-required set")
    void serviceOriginatedHeaders_carryAllFourHardRequired() {
        HttpHeaders h = new HttpHeaders();
        NhumeClient.applyServiceOriginatedHeaders(h, TENANT);

        // The four V11HeaderFilter hard-required headers:
        assertThat(h.getFirst("X-Tenant-ID")).isEqualTo(TENANT.toString());
        assertThat(h.getFirst("X-Pod-ID")).isEqualTo("national-spine");
        assertThat(h.getFirst("X-Request-ID")).isNotBlank();
        assertThat(h.getFirst("X-Correlation-ID")).isNotBlank(); // the BUG-10 omission
        // Actor context for the system hop:
        assertThat(h.getFirst("X-Actor-ID")).isEqualTo("msika-flow-service");
        assertThat(h.getFirst("X-Actor-Type")).isEqualTo("SYSTEM");
        assertThat(h.getFirst("X-Purpose-Of-Use")).isEqualTo("LOGISTICS");
    }

    @Test
    @DisplayName("BUG-4: liability idempotency key is deterministic per logical call, distinct across calls")
    void liabilityIdempotencyKey_deterministicPerLogicalCall() {
        UUID coverageId = UUID.randomUUID();
        String a = CoverageClient.liabilityIdempotencyKey(coverageId, "C09AA05", new BigDecimal("25.00"));
        String b = CoverageClient.liabilityIdempotencyKey(coverageId, "C09AA05", new BigDecimal("25.00"));
        String differentCharge = CoverageClient.liabilityIdempotencyKey(coverageId, "C09AA05", new BigDecimal("30.00"));
        String differentBenefit = CoverageClient.liabilityIdempotencyKey(coverageId, "C03CA01", new BigDecimal("25.00"));

        assertThat(a).isEqualTo(b)                     // retry replays, never 409s
                .isNotEqualTo(differentCharge)         // re-quote is a NEW logical call
                .isNotEqualTo(differentBenefit)
                .startsWith("msika-flow-liability:");
        // 25.00 vs 25.0 must normalise to the same logical call:
        assertThat(CoverageClient.liabilityIdempotencyKey(coverageId, "C09AA05", new BigDecimal("25.0")))
                .isEqualTo(a);
    }

    @Test
    @DisplayName("BUG-4: PA submission key is body-exact — same bytes replay, ANY body change is a new key")
    void paSubmissionIdempotencyKey_isBodyExact() {
        UUID coverageId = UUID.randomUUID();
        String body = "{\"coverageId\":\"" + coverageId + "\",\"authType\":\"PRIOR\","
                + "\"lines\":[{\"benefitCode\":\"C09AA05\",\"estimatedAmount\":20.00}]}";
        String sameBody = new String(body.toCharArray());
        String changedAmount = body.replace("20.00", "99.00");
        String changedAuthType = body.replace("PRIOR", "CONCURRENT");

        String a = CoverageClient.paSubmissionIdempotencyKey(coverageId, body);
        assertThat(a)
                .isEqualTo(CoverageClient.paSubmissionIdempotencyKey(coverageId, sameBody))
                .isNotEqualTo(CoverageClient.paSubmissionIdempotencyKey(coverageId, changedAmount))
                // fix-run-2 live finding: a key blind to authType 409'd
                // IDENTITY_CONFLICT when the auth vocabulary was corrected
                .isNotEqualTo(CoverageClient.paSubmissionIdempotencyKey(coverageId, changedAuthType))
                .startsWith("msika-flow-pa:" + coverageId + ":");
    }
}
