package zw.gov.mohcc.impilo.pct.integration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * The continuum-link call — the pct→daidzai flow that closes CC-5 V-3 on facility arrival.
 *
 * <p>The properties under test are the care-first ones: it reaches the right endpoint with the
 * journey, and it NEVER throws on a downstream failure, because a clinical write must not roll back
 * because DAIDZAI is unreachable or reports a correlation conflict. That "never throws" is the whole
 * reason the link is best-effort and the unanchored sweep exists to catch a link that never landed.
 */
class DaidzaiEpisodeClientTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID EPISODE = UUID.fromString("11111111-0000-4000-8000-000000000001");

    @Test
    void continuumLinkPostsTheJourneyToTheEpisodeEndpoint() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        DaidzaiEpisodeClient client = new DaidzaiEpisodeClient(rt, "http://daidzai.test");

        client.continuumLink(TENANT, EPISODE, "J-0001", null);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> body = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rt).exchange(url.capture(), eq(HttpMethod.POST), body.capture(), eq(Void.class));

        assertThat(url.getValue())
                .isEqualTo("http://daidzai.test/internal/v1/daidzai/trauma-episodes/" + EPISODE + "/continuum-link");
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) body.getValue().getBody();
        assertThat(sent).containsEntry("pctJourneyId", "J-0001");
    }

    @Test
    void continuumLinkIsANoOpWithoutAnEpisodeOrAJourney() {
        RestTemplate rt = mock(RestTemplate.class);
        DaidzaiEpisodeClient client = new DaidzaiEpisodeClient(rt, "http://daidzai.test");

        client.continuumLink(TENANT, null, "J-1", null);        // no episode
        client.continuumLink(TENANT, EPISODE, null, null);       // no journey
        client.continuumLink(TENANT, EPISODE, "  ", null);       // blank journey

        // A patient with no trauma episode, or an arrival with no journey yet, is not an error — it
        // is simply nothing to link. Do not call DAIDZAI at all.
        verify(rt, never()).exchange(any(String.class), any(), any(), eq(Void.class));
    }

    @Test
    void continuumLinkNeverThrowsWhenDaidzaiIsUnreachable() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        DaidzaiEpisodeClient client = new DaidzaiEpisodeClient(rt, "http://daidzai.test");

        // The ED write must proceed even if DAIDZAI is down; the sweep catches the missing link.
        assertThatCode(() -> client.continuumLink(TENANT, EPISODE, "J-1", null)).doesNotThrowAnyException();
    }

    @Test
    void continuumLinkNeverThrowsOnAConflict() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Void.class)))
                .thenThrow(HttpClientErrorException.create(CONFLICT, "Conflict", null, null, null));
        DaidzaiEpisodeClient client = new DaidzaiEpisodeClient(rt, "http://daidzai.test");

        // A 409 means the episode is anchored to a different journey — a real correlation anomaly,
        // but still not a reason to roll back the clinical write. Logged loudly, not thrown.
        assertThatCode(() -> client.continuumLink(TENANT, EPISODE, "J-1", null)).doesNotThrowAnyException();
    }
}
