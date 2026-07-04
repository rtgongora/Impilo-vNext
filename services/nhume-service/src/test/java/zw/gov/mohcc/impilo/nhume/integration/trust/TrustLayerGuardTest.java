package zw.gov.mohcc.impilo.nhume.integration.trust;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/** Trust guard: sensitive Nhume actions require purpose-of-use + actor provenance. */
class TrustLayerGuardTest {

    TrustLayerGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TrustLayerGuard();
        ReflectionTestUtils.setField(guard, "enforcePurposeOfUse", true);
    }

    private TrustLayerGuard.ActorContext ctx(String actorId, String actorType, String purpose) {
        return new TrustLayerGuard.ActorContext(null, actorId, actorType, purpose,
                null, null, null, null);
    }

    @Test
    void missing_purpose_of_use_is_denied_with_code() {
        assertThatThrownBy(() -> guard.requirePurposeOfUse(ctx("a-1", "PROVIDER", null), "dispatch"))
                .isInstanceOf(TrustLayerGuard.TrustDeniedException.class)
                .satisfies(t -> assertThat(((TrustLayerGuard.TrustDeniedException) t).getCode())
                        .isEqualTo("PURPOSE_OF_USE_REQUIRED"));
    }

    @Test
    void blank_purpose_of_use_is_denied() {
        assertThatThrownBy(() -> guard.requirePurposeOfUse(ctx("a-1", "PROVIDER", "  "), "dispatch"))
                .isInstanceOf(TrustLayerGuard.TrustDeniedException.class);
    }

    @Test
    void present_purpose_of_use_passes() {
        assertThatCode(() -> guard.requirePurposeOfUse(ctx("a-1", "PROVIDER", "LOGISTICS"), "dispatch"))
                .doesNotThrowAnyException();
    }

    @Test
    void actor_requires_both_id_and_type() {
        assertThatThrownBy(() -> guard.requireActor(ctx("a-1", null, "LOGISTICS"), "assign"))
                .isInstanceOf(TrustLayerGuard.TrustDeniedException.class)
                .satisfies(t -> assertThat(((TrustLayerGuard.TrustDeniedException) t).getCode())
                        .isEqualTo("ACTOR_REQUIRED"));
        assertThatThrownBy(() -> guard.requireActor(ctx(null, "PROVIDER", "LOGISTICS"), "assign"))
                .isInstanceOf(TrustLayerGuard.TrustDeniedException.class);
        assertThatCode(() -> guard.requireActor(ctx("a-1", "PROVIDER", "LOGISTICS"), "assign"))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcement_off_bypasses_both_checks() {
        ReflectionTestUtils.setField(guard, "enforcePurposeOfUse", false);
        assertThatCode(() -> {
            guard.requirePurposeOfUse(ctx(null, null, null), "dispatch");
            guard.requireActor(ctx(null, null, null), "assign");
        }).doesNotThrowAnyException();
    }
}
