package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.khuluma.core.FeedbackRoutingService.FeedbackKind;
import zw.gov.mohcc.impilo.khuluma.core.FeedbackRoutingService.RoutingDecision;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackRoutingServiceTest {

    private final FeedbackRoutingService service = new FeedbackRoutingService();

    @Test
    void quality_safety_kinds_route_to_rito_with_a_valid_signal_body() {
        for (FeedbackKind k : new FeedbackKind[]{
                FeedbackKind.COMPLAINT, FeedbackKind.SAFETY_CONCERN,
                FeedbackKind.QUALITY_ISSUE, FeedbackKind.ADVERSE_EXPERIENCE}) {
            RoutingDecision d = service.route(k, "HIGH", "khuluma-conv-1", "Long wait, no triage", "khuluma");
            assertThat(d.routeToRito()).as(k.name()).isTrue();
            // The body must carry Rito's two required fields.
            assertThat(d.signalBody()).containsEntry("signal_type", k.name());
            assertThat(d.signalBody()).containsEntry("source_system", "khuluma");
            assertThat(d.signalBody()).containsEntry("severity", "HIGH");
            assertThat(d.signalBody()).containsEntry("source_ref", "khuluma-conv-1");
        }
    }

    @Test
    void non_concern_kinds_do_not_route() {
        for (FeedbackKind k : new FeedbackKind[]{FeedbackKind.PRAISE, FeedbackKind.QUERY, FeedbackKind.OTHER}) {
            RoutingDecision d = service.route(k, null, "ref", "msg", "khuluma");
            assertThat(d.routeToRito()).as(k.name()).isFalse();
            assertThat(d.signalBody()).isNull();
            assertThat(d.reason()).isEqualTo("NOT_A_QUALITY_SAFETY_CONCERN");
        }
    }

    @Test
    void null_kind_is_treated_as_other_and_does_not_route() {
        assertThat(service.route(null, null, null, null, null).routeToRito()).isFalse();
    }

    @Test
    void source_system_defaults_to_khuluma_when_unset() {
        RoutingDecision d = service.route(FeedbackKind.SAFETY_CONCERN, null, null, null, null);
        assertThat(d.signalBody()).containsEntry("source_system", "khuluma");
        // Optional fields are omitted when absent (no null pollution into the Rito body).
        assertThat(d.signalBody()).doesNotContainKey("severity");
        assertThat(d.signalBody()).doesNotContainKey("source_ref");
    }

    @Test
    void nompilo_handoff_sourced_feedback_carries_its_origin() {
        RoutingDecision d = service.route(FeedbackKind.SAFETY_CONCERN, "CRITICAL", "handoff-9", "AI flagged risk", "nompilo");
        assertThat(d.routeToRito()).isTrue();
        assertThat(d.signalBody()).containsEntry("source_system", "nompilo");
        assertThat(d.signalBody()).containsEntry("source_ref", "handoff-9");
    }
}
