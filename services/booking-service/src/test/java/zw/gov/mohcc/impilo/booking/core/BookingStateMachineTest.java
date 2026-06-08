package zw.gov.mohcc.impilo.booking.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.booking.domain.ActorType;
import zw.gov.mohcc.impilo.booking.domain.BookingStatus;
import zw.gov.mohcc.impilo.booking.domain.BookingType;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingStateMachineTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private BookingAuditService auditService;

    private BookingStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new BookingStateMachine(auditService);
        TrustContextHolder.set(new TrustContext(
                TENANT_ID, "actor-1", "STAFF", "TREATMENT", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void allowsRequestedToTriaged() {
        assertThat(stateMachine.canTransition(BookingStatus.REQUESTED, BookingStatus.TRIAGED)).isTrue();
    }

    @Test
    void rejectsCancelledToConfirmed() {
        assertThat(stateMachine.canTransition(BookingStatus.CANCELLED, BookingStatus.CONFIRMED)).isFalse();
    }

    @Test
    void transition_recordsAuditAndUpdatesStatus() {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setTenantId(TENANT_ID);
        booking.setBookingNumber("BK-TEST");
        booking.setClientId("CPID-1");
        booking.setRequestedByActorId("actor-1");
        booking.setRequestedByActorType(ActorType.STAFF);
        booking.setBookingType(BookingType.CONSULTATION);
        booking.setBookingStatus(BookingStatus.REQUESTED);
        booking.setCreatedBy("actor-1");

        BookingEntity result = stateMachine.transition(ctx, booking, BookingStatus.TRIAGED, "TRIAGE");

        assertThat(result.getBookingStatus()).isEqualTo(BookingStatus.TRIAGED);
        verify(auditService).record(eq(ctx), eq(booking.getId()), eq("TRIAGE"),
                eq(BookingStatus.REQUESTED), eq(BookingStatus.TRIAGED), anyMap());
    }

    @Test
    void transition_throwsOnInvalidPath() {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setBookingStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> stateMachine.transition(ctx, booking, BookingStatus.CONFIRMED, "CONFIRM"))
                .isInstanceOf(IllegalStateException.class);
    }
}
