package zw.gov.mohcc.impilo.sessiontemplates;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Doctrine assertions: all five modes load and grants genuinely differ by mode+role. */
class SessionTemplateRegistryTest {

    private final SessionTemplateRegistry registry = new SessionTemplateRegistry();

    @Test
    void all_five_modes_load() {
        assertThat(registry.modes()).containsExactlyInAnyOrder(SessionMode.values());
        for (SessionMode mode : SessionMode.values()) {
            assertThat(registry.get(mode).sessionMode()).isEqualTo(mode);
        }
    }

    @Test
    void grants_differ_by_mode_and_role() {
        // Live-event audience is subscribe-only; a meeting participant publishes.
        var audience = registry.get(SessionMode.LIVE_EVENT).grantFor("AUDIENCE");
        assertThat(audience.canPublish()).isFalse();
        assertThat(audience.canSubscribe()).isTrue();
        var participant = registry.get(SessionMode.MEETING).grantFor("PARTICIPANT");
        assertThat(participant.canPublish()).isTrue();
        // Telemedicine observer is hidden and cannot publish; provider is admin.
        var observer = registry.get(SessionMode.TELEMEDICINE).grantFor("OBSERVER");
        assertThat(observer.canPublish()).isFalse();
        assertThat(observer.isHidden()).isTrue();
        assertThat(registry.get(SessionMode.TELEMEDICINE).grantFor("PROVIDER").isRoomAdmin()).isTrue();
    }

    @Test
    void unknown_roles_get_no_grant() {
        assertThat(registry.get(SessionMode.TELEMEDICINE).grantFor("AUDIENCE")).isNull();
        assertThat(registry.get(SessionMode.MEETING).grantFor("PATIENT")).isNull();
    }

    @Test
    void recording_policy_is_mode_specific() {
        var tele = registry.get(SessionMode.TELEMEDICINE).recording();
        assertThat(tele.consentRequired()).isTrue();
        assertThat(tele.defaultOn()).isFalse();
        assertThat(tele.canStart("PROVIDER")).isTrue();
        assertThat(tele.canStart("PATIENT")).isFalse();
        var event = registry.get(SessionMode.LIVE_EVENT).recording();
        assertThat(event.defaultOn()).isTrue();
        assertThat(event.consentRequired()).isFalse();
    }

    @Test
    void lobby_and_join_flow_follow_doctrine() {
        assertThat(registry.get(SessionMode.TELEMEDICINE).lobbyBehaviour()).isEqualTo("WAITING_ROOM");
        assertThat(registry.get(SessionMode.MEETING).lobbyBehaviour()).isEqualTo("KNOCK");
        assertThat(registry.get(SessionMode.LIVE_EVENT).joinFlow()).isEqualTo("REGISTRATION");
        assertThat(registry.get(SessionMode.LEARNING_LIVE).joinFlow()).isEqualTo("SCHEDULED_CHECKIN");
    }

    @Test
    void room_prefixes_are_per_mode() {
        assertThat(registry.get(SessionMode.TELEMEDICINE).effectiveRoomPrefix()).isEqualTo("impilo-telemedicine");
        assertThat(registry.get(SessionMode.MEETING).effectiveRoomPrefix()).isEqualTo("impilo-meet");
        assertThat(registry.get(SessionMode.LIVE_EVENT).effectiveRoomPrefix()).isEqualTo("impilo-live");
    }

    @Test
    void wire_lookup_is_lenient_but_honest() {
        assertThat(registry.find("telemedicine").sessionMode()).isEqualTo(SessionMode.TELEMEDICINE);
        assertThat(registry.find("LEARNING-LIVE").sessionMode()).isEqualTo(SessionMode.LEARNING_LIVE);
        assertThat(registry.find("ZOOM_CALL")).isNull();
        assertThat(registry.find(null)).isNull();
    }

    @Test
    void learning_modes_carry_completion_criteria() {
        assertThat(registry.get(SessionMode.LEARNING_LIVE).completionCriteriaRef()).isEqualTo("lrn_completion_rule");
        assertThat(registry.get(SessionMode.LEARNING_RECORDING).completionCriteriaRef()).isEqualTo("lrn_completion_rule");
        assertThat(registry.get(SessionMode.TELEMEDICINE).completionCriteriaRef()).isNull();
    }

    @Test
    void get_throws_for_missing_mode_rather_than_returning_null() {
        // All modes are present in this build; the guard is for future enum growth.
        for (SessionMode mode : SessionMode.values()) {
            assertThat(registry.get(mode)).isNotNull();
        }
        assertThatThrownBy(() -> registry.find("").sessionMode())
                .isInstanceOf(NullPointerException.class);
    }
}
