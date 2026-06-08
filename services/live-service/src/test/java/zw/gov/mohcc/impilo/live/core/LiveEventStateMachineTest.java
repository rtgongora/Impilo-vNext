package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveEventStateMachineTest {

    private final LiveEventStateMachine stateMachine = new LiveEventStateMachine();

    @Test
    void allowsDraftToScheduled() {
        assertThat(stateMachine.canTransition(LiveEventStatus.DRAFT, LiveEventStatus.SCHEDULED)).isTrue();
    }

    @Test
    void allowsScheduledToLive() {
        assertThat(stateMachine.canTransition(LiveEventStatus.SCHEDULED, LiveEventStatus.LIVE)).isTrue();
    }

    @Test
    void allowsLiveToEnded() {
        assertThat(stateMachine.canTransition(LiveEventStatus.LIVE, LiveEventStatus.ENDED)).isTrue();
    }

    @Test
    void allowsEndedToProcessingReplay() {
        assertThat(stateMachine.canTransition(LiveEventStatus.ENDED, LiveEventStatus.PROCESSING_REPLAY)).isTrue();
    }

    @Test
    void allowsProcessingReplayToPublishedReplay() {
        assertThat(stateMachine.canTransition(LiveEventStatus.PROCESSING_REPLAY, LiveEventStatus.PUBLISHED_REPLAY)).isTrue();
    }

    @Test
    void rejectsCancelledToLive() {
        assertThat(stateMachine.canTransition(LiveEventStatus.CANCELLED, LiveEventStatus.LIVE)).isFalse();
    }

    @Test
    void transition_updatesStatus() {
        LiveEventEntity event = new LiveEventEntity();
        event.setStatus(LiveEventStatus.DRAFT.name());
        LiveEventEntity result = stateMachine.transition(event, LiveEventStatus.SCHEDULED, "host-1");
        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.getUpdatedBy()).isEqualTo("host-1");
    }

    @Test
    void transition_throwsOnInvalidPath() {
        LiveEventEntity event = new LiveEventEntity();
        event.setStatus(LiveEventStatus.ARCHIVED.name());
        assertThatThrownBy(() -> stateMachine.transition(event, LiveEventStatus.LIVE, "host"))
                .isInstanceOf(IllegalStateException.class);
    }
}
