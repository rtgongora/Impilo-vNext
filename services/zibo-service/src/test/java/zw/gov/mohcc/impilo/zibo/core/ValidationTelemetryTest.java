package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.ValidationOutcome;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The validation log must record what happened, including when nothing went wrong.
 *
 * <p>{@code zibo_validation_logs} held zero rows, and even a full table could not have produced a
 * coverage number: {@code logValidation} fired on exactly two paths, both failures, so failures
 * counted against no denominator. A hundred of them might have been a catastrophe or a rounding
 * error, and nothing on record could tell you which. That is why no one in this estate could state
 * how coded it is.</p>
 *
 * <p>The existing {@code ValidationEngineTest} mocks {@code validationLogRepository} and never
 * verifies {@code save}, so the logging path had no cover at all. These tests assert the row.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidationTelemetryTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID NATIONAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SYSTEM = "http://www.whocc.no/atc";

    @Mock private ArtifactRepository artifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        ZiboProperties props = new ZiboProperties();
        props.getValidation().setDefaultMode("LENIENT");
        engine = new ValidationEngine(artifactRepository, assignmentRepository,
                validationJobRepository, validationLogRepository, outboxRepository, props,
                new ObjectMapper(),
                new ArtifactResolutionService(artifactRepository, new SimpleMeterRegistry(),
                        NATIONAL.toString()),
                new SimpleMeterRegistry());
        when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(any(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    private static ArtifactEntity codeSystem(UUID tenant, String version, String sortKey, String json) {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setCanonicalUrl(SYSTEM);
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setVersion(version);
        a.setVersionSortKey(sortKey);
        a.setPublishedAt(OffsetDateTime.now());
        a.setContentJson(json);
        return a;
    }

    private static String withCodes(String... codes) {
        StringBuilder sb = new StringBuilder("{\"resourceType\":\"CodeSystem\",\"concept\":[");
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"code\":\"").append(codes[i]).append("\",\"display\":\"x\"}");
        }
        return sb.append("]}").toString();
    }

    private ValidationLogEntity runAndCaptureLog(Runnable action) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            TrustContext ctx = new TrustContext(TENANT, "oros-service", "SYSTEM", "TREATMENT",
                    null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
            holder.when(TrustContextHolder::require).thenReturn(ctx);
            holder.when(TrustContextHolder::get).thenReturn(ctx);
            action.run();
        }
        ArgumentCaptor<ValidationLogEntity> cap = ArgumentCaptor.forClass(ValidationLogEntity.class);
        verify(validationLogRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    // ── The denominator ───────────────────────────────────────────────────────────────────────

    @Test
    void aSuccessfulValidationIsRecorded_becauseItIsTheDenominator() {
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(codeSystem(TENANT, "2026.01", "002026.000001.000000",
                        withCodes("N02BE01"))));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "N02BE01", null, null, null));

        assertThat(row.getResult())
                .describedAs("without RESOLVED rows the table can count failures but never a "
                        + "percentage — which is exactly the state this fixes")
                .isEqualTo(ValidationOutcome.RESOLVED);
        assertThat(row.getSystem()).isEqualTo(SYSTEM);
        assertThat(row.getCode()).isEqualTo("N02BE01");
        assertThat(row.getVersion()).isEqualTo("2026.01");
    }

    @Test
    void anUnknownCodeRecordsTheCode_notJustProse() {
        // The old table had no `code` column at all: "which code failed" was recoverable only by
        // string-parsing the details blob.
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(codeSystem(TENANT, "2026.01", "002026.000001.000000",
                        withCodes("N02BE01"))));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "NOT-A-REAL-CODE", null, null, null));

        assertThat(row.getResult()).isEqualTo(ValidationOutcome.UNKNOWN_CODE);
        assertThat(row.getCode()).isEqualTo("NOT-A-REAL-CODE");
    }

    @Test
    void anUnknownSystemIsDistinctFromAnUnknownCode() {
        // oros validates against urn:zibo:order-codes, which is no artifact's canonical URL. That
        // is a different problem from a bad code and must be countable separately.
        when(artifactRepository.findCandidatesAcrossPlanes(any(), any(), any())).thenReturn(List.of());

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding("urn:zibo:order-codes", "FBC", null, null, null));

        assertThat(row.getResult()).isEqualTo(ValidationOutcome.UNKNOWN_SYSTEM);
        assertThat(row.getSystem()).isEqualTo("urn:zibo:order-codes");
    }

    @Test
    void theCallingServiceIsRecorded_notTheHardcodedLiteral() {
        // service_name has always been "zibo-service", so it cannot distinguish oros from butano.
        when(artifactRepository.findCandidatesAcrossPlanes(any(), any(), any())).thenReturn(List.of());

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "X", null, null, null));

        assertThat(row.getCallingService()).isEqualTo("oros-service");
        assertThat(row.getCorrelationId()).isNotBlank();
    }

    @Test
    void telemetryFailureNeverFailsTheValidation() {
        // A measurement must not become an outage.
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(codeSystem(TENANT, "1.0.0", "000001.000000.000000",
                        withCodes("A"))));
        when(validationLogRepository.save(any())).thenThrow(new RuntimeException("log table gone"));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            TrustContext ctx = new TrustContext(TENANT, "svc", "SYSTEM", "TREATMENT", null,
                    UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
            holder.when(TrustContextHolder::require).thenReturn(ctx);
            holder.when(TrustContextHolder::get).thenReturn(ctx);

            assertThat(engine.validateCoding(SYSTEM, "A", null, null, null).valid()).isTrue();
        }
    }

    // ── Resolution: version, window, plane ────────────────────────────────────────────────────

    @Test
    void theHighestVersionWins_notTheMostRecentlyPublished() {
        // The defect: a 1.0.1 correction published after 1.1.0 was served as current.
        ArtifactEntity older = codeSystem(TENANT, "1.1.0", "000001.000001.000000", withCodes("NEW"));
        ArtifactEntity newerByClock = codeSystem(TENANT, "1.0.1", "000001.000000.000001", withCodes("OLD"));
        newerByClock.setPublishedAt(OffsetDateTime.now().plusDays(1));
        // Repository returns version_sort_key DESC, which is the fix.
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(older, newerByClock));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "NEW", null, null, null));

        assertThat(row.getResult()).isEqualTo(ValidationOutcome.RESOLVED);
        assertThat(row.getVersion()).isEqualTo("1.1.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nationalTerminologyResolvesFromTheCarePlane() {
        // Every lookup was findByTenantId…, so a national registry seeded only in REGISTRY was
        // invisible to CARE. That is why V007 inserts ATC twice.
        //
        // This asserts the QUERY, not just the answer. An earlier version stubbed the tenant list
        // with any() and passed even with the national plane removed from planesFor() — a check
        // that could not fail for the reason it existed.
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(codeSystem(NATIONAL, "2026.01", "002026.000001.000000",
                        withCodes("N02BE01"))));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "N02BE01", null, null, null));

        assertThat(row.getResult()).isEqualTo(ValidationOutcome.RESOLVED);

        ArgumentCaptor<java.util.Collection<UUID>> planes =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(artifactRepository).findCandidatesAcrossPlanes(
                eq(SYSTEM), planes.capture(), eq(ArtifactType.CODE_SYSTEM));
        assertThat(planes.getValue())
                .describedAs("a CARE-plane request must also look in the national registry plane, "
                        + "or governed terminology stays invisible across the boundary")
                .contains(NATIONAL)
                .contains(TENANT);
    }

    @Test
    void aTenantCopyBeatsTheNationalOne() {
        ArtifactEntity national = codeSystem(NATIONAL, "2026.01", "002026.000001.000000", withCodes("NAT"));
        ArtifactEntity local = codeSystem(TENANT, "2025.1", "002025.000001.000000", withCodes("LOCAL"));
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(national, local));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "LOCAL", null, null, null));

        assertThat(row.getResult())
                .describedAs("a tenant override must win even when the national copy is newer")
                .isEqualTo(ValidationOutcome.RESOLVED);
    }

    @Test
    void anArtifactOutsideItsEffectiveWindowIsNotCurrent() {
        // effective_start/effective_end were added by V003 and read by nothing.
        ArtifactEntity notYet = codeSystem(TENANT, "2.0.0", "000002.000000.000000", withCodes("FUTURE"));
        notYet.setEffectiveStart(OffsetDateTime.now().plusYears(1));
        ArtifactEntity current = codeSystem(TENANT, "1.0.0", "000001.000000.000000", withCodes("NOW"));
        when(artifactRepository.findCandidatesAcrossPlanes(eq(SYSTEM), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(notYet, current));

        ValidationLogEntity row = runAndCaptureLog(
                () -> engine.validateCoding(SYSTEM, "NOW", null, null, null));

        assertThat(row.getVersion())
                .describedAs("a future-dated artifact must not become current merely by being the "
                        + "highest version")
                .isEqualTo("1.0.0");
    }
}
