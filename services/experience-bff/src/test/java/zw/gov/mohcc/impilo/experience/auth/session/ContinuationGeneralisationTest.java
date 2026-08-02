package zw.gov.mohcc.impilo.experience.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The continuation store was already opaque, short-lived and single-use — what made it
 * recovery-scoped was that it carried nothing but a destination. A step-up or consent challenge
 * cannot be re-presented after a redirect it cannot describe.
 *
 * <p>Generalising it opens a hole worth naming: a continuation crosses an <em>unauthenticated</em>
 * redirect through Keycloak and back, so anything stored in it outlives the request that created
 * it. A free-form state bag on that path is where a token or a patient identifier ends up by
 * accident. Hence an allowlist, tested here as hard as the resume behaviour itself.</p>
 */
class ContinuationGeneralisationTest {

    private WebAuthSessionStore store;
    private Map<String, String> redisData;

    @BeforeEach
    void setUp() throws Exception {
        redisData = new ConcurrentHashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(inv -> {
            redisData.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), any());
        when(ops.getAndDelete(anyString())).thenAnswer(inv -> redisData.remove(inv.getArgument(0)));

        WebAuthSessionProperties props = new WebAuthSessionProperties();
        props.setEnabled(true);
        props.setClientSecret("test-secret");
        props.setEncryptionKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        store = new WebAuthSessionStore(redis, new ObjectMapper().findAndRegisterModules(), props);
        // @PostConstruct is not run by the container here, and it is what derives the key.
        var m = WebAuthSessionStore.class.getDeclaredMethod("validateConfiguration");
        m.setAccessible(true);
        m.invoke(store);
    }

    @Test
    @DisplayName("a challenge continuation carries the kind and state needed to re-present it")
    void carriesChallengeState() {
        String id = store.saveContinuation("/patients/1/notes", "CONSENT_REQUIRED",
                Map.of("reasonCode", "CONSENT_REQUIRED", "requiredAction", "OBTAIN_CONSENT"));

        Optional<WebAuthSessionStore.Continuation> resumed = store.consumeContinuationRecord(id);

        assertThat(resumed).isPresent();
        assertThat(resumed.get().returnTo()).isEqualTo("/patients/1/notes");
        assertThat(resumed.get().challengeKind()).isEqualTo("CONSENT_REQUIRED");
        assertThat(resumed.get().state()).containsEntry("requiredAction", "OBTAIN_CONSENT");
    }

    @Test
    @DisplayName("a state key outside the allowlist is REFUSED, not silently dropped")
    void rejectsUnknownStateKeys() {
        // Silently dropping would be worse than refusing: the caller believes the challenge can be
        // resumed with context it does not actually have.
        Map<String, String> smuggled = new HashMap<>();
        smuggled.put("subjectRef", "CPID-9");

        assertThatThrownBy(() -> store.saveContinuation("/x", "STEP_UP_REQUIRED", smuggled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subjectRef");
    }

    @Test
    @DisplayName("the allowlist names nothing that identifies a person")
    void allowlistIdentifiesNobody() {
        assertThat(WebAuthSessionStore.ALLOWED_CONTINUATION_STATE_KEYS)
                .doesNotContain("subjectRef", "actorId", "cpid", "patientId", "accessToken",
                        "idToken", "refreshToken", "healthId");
    }

    @Test
    @DisplayName("an oversized value is refused — a continuation is not a data channel")
    void rejectsOversizedValues() {
        assertThatThrownBy(() -> store.saveContinuation("/x", "STEP_UP_REQUIRED",
                Map.of("reasonCode", "A".repeat(257))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("single-use survives generalisation — the record path cannot be replayed")
    void recordPathIsStillSingleUse() {
        String id = store.saveContinuation("/x", "STEP_UP_REQUIRED", Map.of());

        assertThat(store.consumeContinuationRecord(id)).isPresent();
        assertThat(store.consumeContinuationRecord(id))
                .as("a second read would let a challenge be resumed twice from one redirect")
                .isEmpty();
    }

    @Test
    @DisplayName("reading the record then redeeming the destination is ALSO one use")
    void recordAndDestinationShareOneUse() {
        // Two independent read paths would defeat single-use entirely: read the record for
        // display, then still redeem the destination.
        String id = store.saveContinuation("/x", "CONSENT_REQUIRED", Map.of());

        assertThat(store.consumeContinuationRecord(id)).isPresent();
        assertThat(store.consumeContinuation(id)).isEmpty();
    }

    @Test
    @DisplayName("the recovery-scoped signature still works unchanged")
    void recoveryPathUndisturbed() {
        // OidcSessionService calls the one-argument form. If generalisation broke it, constrained
        // recovery would silently stop resuming.
        String id = store.saveContinuation("/account/security");

        assertThat(store.consumeContinuation(id)).contains("/account/security");
    }

    @Test
    @DisplayName("the stored value is not readable as plaintext")
    void continuationIsEncryptedAtRest() {
        store.saveContinuation("/patients/1/notes", "CONSENT_REQUIRED", Map.of());

        assertThat(redisData.values())
                .as("a continuation naming a clinical destination must not sit in Redis in the clear")
                .noneMatch(v -> v.contains("/patients/1/notes"));
        assertThat(redisData).isNotEmpty();
    }
}
