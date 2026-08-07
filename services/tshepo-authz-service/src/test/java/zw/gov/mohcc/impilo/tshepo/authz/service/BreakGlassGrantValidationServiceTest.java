package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.BreakGlassRequestEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationGrantEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.BreakGlassRequestRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.VisibilityEscalationGrantRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The grant-validation seam that madi's and pct's emergency guards consult.
 *
 * <p>The behaviour under test is narrow on purpose: two grant models, one closed question, and a
 * NO_GRANT that must be a real answer rather than an error the caller has to interpret.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreakGlassGrantValidationServiceTest {

    @Mock private BreakGlassRequestRepository breakGlassRepository;
    @Mock private VisibilityEscalationGrantRepository escalationGrantRepository;

    private BreakGlassGrantValidationService service;

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR = "actor-123";
    private static final UUID GRANT_TOKEN = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        service = new BreakGlassGrantValidationService(breakGlassRepository, escalationGrantRepository);
        when(escalationGrantRepository.findActiveGrant(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(breakGlassRepository.findActiveByTenantIdAndActorId(any(), any(), any())).thenReturn(List.of());
    }

    private BreakGlassGrantValidationRequest request(String grantToken) {
        return new BreakGlassGrantValidationRequest(TENANT, ACTOR, grantToken, "O_NEG_EMERGENCY_RELEASE");
    }

    private VisibilityEscalationGrantEntity activeEscalationGrant() {
        VisibilityEscalationGrantEntity g = new VisibilityEscalationGrantEntity();
        g.setGrantToken(GRANT_TOKEN);
        g.setTenantId(TENANT);
        g.setActorId(ACTOR);
        g.setStatus("ACTIVE");
        g.setExpiresAt(Instant.now().plusSeconds(3600));
        return g;
    }

    private BreakGlassRequestEntity activeBreakGlassRequest() {
        BreakGlassRequestEntity b = new BreakGlassRequestEntity();
        b.setId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        b.setTenantId(TENANT);
        b.setActorId(ACTOR);
        b.setReason("Uncrossmatched release, unconscious trauma patient");
        b.setReviewStatus("PENDING_REVIEW");
        b.setGrantedAt(Instant.now());
        b.setExpiresAt(Instant.now().plusSeconds(3600));
        return b;
    }

    @Test
    @DisplayName("no grant of either kind: verdict is NO_GRANT, and it is an answer rather than an error")
    void noGrantAtAll() {
        BreakGlassGrantValidationResponse response = service.validate(request(null));

        assertEquals(BreakGlassGrantValidationResponse.VERDICT_NO_GRANT, response.verdict());
        assertNull(response.source());
        assertNull(response.grantRef());
    }

    @Test
    @DisplayName("active escalation grant matching tenant+actor: VALID, sourced to the token")
    void activeEscalationGrantIsValid() {
        when(escalationGrantRepository.findActiveGrant(eq(GRANT_TOKEN), eq(TENANT), eq(ACTOR), any()))
                .thenReturn(Optional.of(activeEscalationGrant()));

        BreakGlassGrantValidationResponse response = service.validate(request(GRANT_TOKEN.toString()));

        assertEquals(BreakGlassGrantValidationResponse.VERDICT_VALID, response.verdict());
        assertEquals(BreakGlassGrantValidationResponse.SOURCE_ESCALATION_GRANT, response.source());
        assertEquals(GRANT_TOKEN.toString(), response.grantRef());
    }

    @Test
    @DisplayName("active break-glass request with no token: VALID, sourced to the declaration")
    void activeBreakGlassRequestIsValid() {
        when(breakGlassRepository.findActiveByTenantIdAndActorId(eq(TENANT), eq(ACTOR), any()))
                .thenReturn(List.of(activeBreakGlassRequest()));

        BreakGlassGrantValidationResponse response = service.validate(request(null));

        assertEquals(BreakGlassGrantValidationResponse.VERDICT_VALID, response.verdict());
        assertEquals(BreakGlassGrantValidationResponse.SOURCE_BREAK_GLASS_REQUEST, response.source());
        assertEquals("PENDING_REVIEW", response.reviewStatus());
    }

    @Test
    @DisplayName("a malformed grant token does not suppress a genuine break-glass declaration")
    void malformedTokenFallsThroughToActorScopedCheck() {
        when(breakGlassRepository.findActiveByTenantIdAndActorId(eq(TENANT), eq(ACTOR), any()))
                .thenReturn(List.of(activeBreakGlassRequest()));

        BreakGlassGrantValidationResponse response = service.validate(request("not-a-uuid"));

        // Were a malformed token to short-circuit, a forged header would become a denial-of-care:
        // an attacker could switch off someone else's emergency access by attaching junk to it.
        assertEquals(BreakGlassGrantValidationResponse.VERDICT_VALID, response.verdict());
        assertEquals(BreakGlassGrantValidationResponse.SOURCE_BREAK_GLASS_REQUEST, response.source());
        verify(escalationGrantRepository, never()).findActiveGrant(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unresolvable token also falls through rather than short-circuiting")
    void unresolvableTokenFallsThroughToActorScopedCheck() {
        when(breakGlassRepository.findActiveByTenantIdAndActorId(eq(TENANT), eq(ACTOR), any()))
                .thenReturn(List.of(activeBreakGlassRequest()));

        BreakGlassGrantValidationResponse response = service.validate(request(GRANT_TOKEN.toString()));

        assertEquals(BreakGlassGrantValidationResponse.VERDICT_VALID, response.verdict());
        assertEquals(BreakGlassGrantValidationResponse.SOURCE_BREAK_GLASS_REQUEST, response.source());
    }

    @Test
    @DisplayName("a grant belonging to another tenant or actor does not answer: the repository query binds both")
    void grantIsBoundToTenantAndActor() {
        UUID otherTenant = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        service.validate(new BreakGlassGrantValidationRequest(
                otherTenant, "someone-else", GRANT_TOKEN.toString(), "O_NEG_EMERGENCY_RELEASE"));

        // The binding is the repository's, not this service's — assert it is actually passed
        // through, because a service that dropped either argument would silently widen every grant
        // in the estate into a global one.
        verify(escalationGrantRepository).findActiveGrant(eq(GRANT_TOKEN), eq(otherTenant), eq("someone-else"), any());
        verify(breakGlassRepository).findActiveByTenantIdAndActorId(eq(otherTenant), eq("someone-else"), any());
    }
}
