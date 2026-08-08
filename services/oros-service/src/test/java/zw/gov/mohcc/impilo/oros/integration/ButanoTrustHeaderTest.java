package zw.gov.mohcc.impilo.oros.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Assembling trust headers must never be able to destroy the clinical record.
 *
 * <p>{@code buildTrustHeaders} called {@code ctx.tenantId().toString()} inside a {@code try} that
 * caught only {@link IllegalStateException}. But {@code TrustContextFilter} never rejects a
 * request — it builds a context from whatever headers arrived, with {@code parseUuid} returning
 * null for anything missing. So {@code require()} succeeded, {@code tenantId()} was null, and the
 * resulting {@link NullPointerException} was caught by neither that {@code catch} nor the
 * {@code RestClientException} catch in each {@code create*} method.</p>
 *
 * <p>It escaped {@code createDiagnosticReport}, which {@code ReportService.createFinal} calls
 * <em>inside</em> {@code @Transactional}. A request without {@code x-tenant-id} therefore rolled
 * back the report a clinician had just authored — to fail a best-effort SHR write that could never
 * have succeeded anyway, because the same missing header would have produced a 403 at BUTANO.</p>
 */
class ButanoTrustHeaderTest {

    private static ButanoIntegration integration() {
        return new ButanoIntegration(mock(RestTemplate.class),
                "http://butano-service:8090", false);
    }

    private static HttpHeaders headers(ButanoIntegration target) throws Exception {
        Method m = ButanoIntegration.class.getDeclaredMethod("buildTrustHeaders");
        m.setAccessible(true);
        return (HttpHeaders) m.invoke(target);
    }

    private static TrustContext ctx(UUID tenantId, UUID correlationId, String actorType,
                                    String purpose) {
        return new TrustContext(tenantId, "lab-tech-1", actorType, purpose, null,
                correlationId, null, null, null, AccessMode.INTERNAL);
    }

    @Test
    void aMissingTenantSkipsThatHeaderAndKeepsTheRest() throws Exception {
        // The defect, exactly: TrustContextFilter hands back a context whose tenantId is null.
        //
        // Asserting "does not throw" would NOT prove the fix — the outer catch(RuntimeException)
        // added alongside it swallows the NPE too, so that assertion passes with the bug restored.
        // It is caught and the whole assembly aborts, so every OTHER header is lost as well.
        // Surviving siblings are therefore the discriminating evidence, and the only assertion
        // here that can fail for the right reason.
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            UUID correlation = UUID.randomUUID();
            holder.when(TrustContextHolder::get)
                    .thenReturn(ctx(null, correlation, "PROVIDER", "TREATMENT"));

            HttpHeaders h = headers(integration());

            assertThat(h.containsKey(TrustContext.H_TENANT_ID))
                    .describedAs("no tenant was supplied, so none is sent")
                    .isFalse();
            assertThat(h.getFirst(TrustContext.H_ACTOR_ID))
                    .describedAs("an unguarded dereference aborts assembly and loses these too — "
                            + "their survival is what proves the guard rather than the catch")
                    .isEqualTo("lab-tech-1");
            assertThat(h.getFirst(TrustContext.H_CORRELATION_ID)).isEqualTo(correlation.toString());
            assertThat(h.getFirst(TrustContext.H_PURPOSE_OF_USE)).isEqualTo("TREATMENT");
        }
    }

    @Test
    void aMissingCorrelationIdDoesNotThrowEither() throws Exception {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get)
                    .thenReturn(ctx(UUID.randomUUID(), null, "PROVIDER", "TREATMENT"));

            ButanoIntegration target = integration();
            assertThatCode(() -> headers(target)).doesNotThrowAnyException();
        }
    }

    @Test
    void noTrustContextAtAllYieldsEmptyHeaders_notAnException() throws Exception {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get).thenReturn(null);

            assertThat(headers(integration()).isEmpty()).isTrue();
        }
    }

    @Test
    void allFiveHeadersButanoRequiresAreSent() throws Exception {
        // BUTANO's HeaderValidationInterceptor answers 403 unless all five are present. Actor-type
        // and purpose-of-use were never sent, so this call could only ever have been refused.
        UUID tenant = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get)
                    .thenReturn(ctx(tenant, correlation, "PROVIDER", "TREATMENT"));

            HttpHeaders h = headers(integration());

            assertThat(h.getFirst(TrustContext.H_TENANT_ID)).isEqualTo(tenant.toString());
            assertThat(h.getFirst(TrustContext.H_ACTOR_ID)).isEqualTo("lab-tech-1");
            assertThat(h.getFirst(TrustContext.H_CORRELATION_ID)).isEqualTo(correlation.toString());
            assertThat(h.getFirst(TrustContext.H_ACTOR_TYPE)).isEqualTo("PROVIDER");
            assertThat(h.getFirst(TrustContext.H_PURPOSE_OF_USE)).isEqualTo("TREATMENT");
        }
    }

    @Test
    void aBlankFieldIsOmittedRatherThanSentEmpty() throws Exception {
        // An empty header is not the same as an absent one — BUTANO treats blank as missing, and
        // sending "" would look like a supplied value in every log between here and there.
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get)
                    .thenReturn(ctx(UUID.randomUUID(), UUID.randomUUID(), "  ", "TREATMENT"));

            assertThat(headers(integration()).containsKey(TrustContext.H_ACTOR_TYPE)).isFalse();
        }
    }
}
