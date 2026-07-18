package zw.gov.mohcc.impilo.tshepo.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.api.AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identity Contract §9 / C4 — the four-dimension assurance vector. Tests the pure
 * {@code deficientAssuranceDimension} logic (no DB), which decides which dimension
 * (if any) blocks an elevated-assurance action and drives a named step-up.
 */
@DisplayName("Assurance vector (PolicyEngine)")
class AssuranceVectorTest {

    /** A PolicyEngine with null collaborators — we only call the pure vector method. */
    private final PolicyEngine engine =
            new PolicyEngine(null, null, null, null, null, null, null);

    private AuthorizationRequest req(Map<String, String> headers) {
        Map<String, String> h = new HashMap<>(headers);
        h.putIfAbsent(TrustHeaders.TENANT_ID, "00000000-0000-0000-0000-000000000001");
        h.putIfAbsent(TrustHeaders.ACTOR_ID, "actor-1");
        h.putIfAbsent(TrustHeaders.ACTOR_TYPE, "PROVIDER");
        h.putIfAbsent(TrustHeaders.PURPOSE_OF_USE, "TREATMENT");
        return AuthorizationRequest.fromHeaders(h::get, "POST", "/api/v1/clients");
    }

    @Test
    @DisplayName("coarse LOA3 alone satisfies proofing — no deficiency")
    void coarseLoa3_passes() {
        assertNull(engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA3"))));
    }

    @Test
    @DisplayName("G-CZO-01: a fresh IAL upgrade lifts effective proofing even when the coarse level is stale")
    void ialUpgrade_liftsEffectiveProofing() {
        // Coarse header stuck at LOA1 (stale ACR), but the self-service IAL upgrade
        // is LOA3 — effective IAL = max(LOA1, LOA3) = LOA3, so no step-up.
        assertNull(engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA1", TrustHeaders.IAL, "LOA3"))));
    }

    @Test
    @DisplayName("low proofing across both signals → IDENTITY_PROOFING deficiency")
    void lowProofing_deficient() {
        assertEquals("IDENTITY_PROOFING", engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA2", TrustHeaders.IAL, "LOA2"))));
    }

    @Test
    @DisplayName("DISPUTED record link blocks regardless of strong proofing")
    void disputedLink_blocks() {
        assertEquals("RECORD_LINK", engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA4",
                        TrustHeaders.RECORD_LINK_CONFIDENCE, "DISPUTED"))));
    }

    @Test
    @DisplayName("proofing met but weak session authentication → AUTHENTICATION deficiency")
    void weakAal_deficient() {
        assertEquals("AUTHENTICATION", engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA3", TrustHeaders.AAL, "LOA1"))));
    }

    @Test
    @DisplayName("proofing met but weak device/session assurance → SESSION deficiency")
    void weakSession_deficient() {
        assertEquals("SESSION", engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA3", TrustHeaders.SESSION_ASSURANCE, "LOA1"))));
    }

    @Test
    @DisplayName("unsupplied AAL/session dimensions do not block (care-first) when proofing is met")
    void unsuppliedDimensions_dontBlock() {
        assertNull(engine.deficientAssuranceDimension(
                req(Map.of(TrustHeaders.ASSURANCE_LEVEL, "LOA3"))));
    }
}
