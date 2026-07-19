package zw.gov.mohcc.impilo.pct.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.pct.api.dto.CheckInByCardRequest;
import zw.gov.mohcc.impilo.pct.core.JourneyStateMachine;
import zw.gov.mohcc.impilo.pct.core.RoutingEngine;
import zw.gov.mohcc.impilo.pct.core.TriageService;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.card.CardVerificationClient;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * B5: card-presentation check-in routes through the shared card-resolve seam. A
 * resolved ACTIVE card starts a journey for the RESOLVED patient (never a keyed
 * CPID); an unresolved or inactive card is rejected — never a fabricated identity.
 */
@ExtendWith(MockitoExtension.class)
class JourneyCardCheckInTest {

    @Mock JourneyStateMachine stateMachine;
    @Mock TriageService triageService;
    @Mock RoutingEngine routingEngine;
    @Mock JourneyRepository journeyRepository;
    @Mock CardVerificationClient cardVerification;

    JourneyController controller;

    private final UUID facility = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new JourneyController(stateMachine, triageService, routingEngine,
                journeyRepository, cardVerification);
    }

    private TrustContext trustContext() {
        return new TrustContext(UUID.randomUUID(), "clerk-1", "STAFF", "TREATMENT",
                null, UUID.randomUUID(), facility, null, null, AccessMode.INTERNAL);
    }

    private CardVerificationClient.Resolution resolution(boolean resolved, String cpid, String status) {
        return new CardVerificationClient.Resolution(resolved, cpid, cpid, "CARD-1", status,
                "PHYSICAL", "CARD_NUMBER");
    }

    @Test
    @DisplayName("active card → journey started for the resolved patient")
    void activeCardStartsJourney() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            String cpid = UUID.randomUUID().toString();
            when(cardVerification.resolve("CARD-1", null, null))
                    .thenReturn(resolution(true, cpid, "ACTIVE"));
            when(stateMachine.createJourney(any(), eq(cpid), any(), any(), any()))
                    .thenReturn(new JourneyEntity());

            ResponseEntity<ApiResponse<JourneyEntity>> r = controller.checkInByCard(
                    new CheckInByCardRequest("CARD-1", null, null, facility, null, null, null));

            assertEquals(200, r.getStatusCode().value());
            verify(stateMachine).createJourney(any(), eq(cpid), any(), any(), any());
        }
    }

    @Test
    @DisplayName("unresolved card → 422, no journey")
    void unresolvedRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            when(cardVerification.resolve(any(), any(), any()))
                    .thenReturn(CardVerificationClient.Resolution.unresolved());

            ResponseEntity<ApiResponse<JourneyEntity>> r = controller.checkInByCard(
                    new CheckInByCardRequest("NOPE", null, null, facility, null, null, null));

            assertEquals(422, r.getStatusCode().value());
            assertThat(r.getBody().error().code()).isEqualTo("CARD_NOT_RESOLVED");
            verify(stateMachine, never()).createJourney(any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("resolved but revoked card → 422 CARD_INACTIVE, no journey")
    void inactiveRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            when(cardVerification.resolve(any(), any(), any()))
                    .thenReturn(resolution(true, UUID.randomUUID().toString(), "REVOKED"));

            ResponseEntity<ApiResponse<JourneyEntity>> r = controller.checkInByCard(
                    new CheckInByCardRequest("CARD-1", null, null, facility, null, null, null));

            assertEquals(422, r.getStatusCode().value());
            assertThat(r.getBody().error().code()).isEqualTo("CARD_INACTIVE");
            verify(stateMachine, never()).createJourney(any(), any(), any(), any(), any());
        }
    }
}
