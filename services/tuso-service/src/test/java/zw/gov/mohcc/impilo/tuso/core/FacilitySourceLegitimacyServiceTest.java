package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySourceLegitimacyDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilitySourceLegitimacyServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;
    @Mock private FacilityCertificateRepository certificateRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilitySourceLegitimacyService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID facilityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new FacilitySourceLegitimacyService(
                facilityRepository, legitimacyRepository, certificateRepository, outboxRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "governance-officer", "ADMIN", "SYSTEM_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        facility = new FacilityEntity();
        facility.setId(42L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setFacilityCode("ZW010125");
        facility.setName("Bangure Clinic");
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.REGISTERED_ACTIVE);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubFacility() {
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(facility));
    }

    private void stubSaveEcho() {
        when(legitimacyRepository.save(any(FacilitySourceLegitimacyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private FacilitySourceLegitimacyEntity row(FacilityLegitimacySource source,
                                               FacilityLegitimacyStatus status,
                                               boolean allowed,
                                               String reason) {
        FacilitySourceLegitimacyEntity e = new FacilitySourceLegitimacyEntity();
        e.setId((long) (source.ordinal() + 1));
        e.setFacilityId(facilityUuid);
        e.setSource(source);
        e.setStatus(status);
        e.setAsOf(Instant.parse("2026-01-01T00:00:00Z"));
        e.setAllowedOnPlatform(allowed);
        e.setReason(reason);
        return e;
    }

    // ── Upsert semantics: one current row per (facility, source) ─────────────────────────────

    @Test
    void upsertCreatesOneRowPerSourceAndOverwritesTheCurrentRow() {
        stubFacility();
        stubSaveEcho();
        FacilitySourceLegitimacyEntity existing =
                row(FacilityLegitimacySource.HPA_LEGAL, FacilityLegitimacyStatus.REGISTERED_CURRENT, true, null);
        existing.setEvidenceRef("HPA_REGISTRATION_NUMBER:HPA-100");
        when(legitimacyRepository.findByFacilityIdAndSource(facilityUuid, FacilityLegitimacySource.HPA_LEGAL))
                .thenReturn(Optional.of(existing));

        var view = service.upsert(facilityUuid, FacilityLegitimacySource.HPA_LEGAL,
                new FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest(
                        FacilityLegitimacyStatus.EXPIRED, Instant.parse("2026-06-01T00:00:00Z"),
                        "HPA_REGISTRATION_NUMBER:HPA-100", false, "Annual registration lapsed"));

        // The SAME row (same id) is overwritten — no second row for the source is created.
        ArgumentCaptor<FacilitySourceLegitimacyEntity> captor =
                ArgumentCaptor.forClass(FacilitySourceLegitimacyEntity.class);
        verify(legitimacyRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getStatus()).isEqualTo(FacilityLegitimacyStatus.EXPIRED);
        assertThat(captor.getValue().isAllowedOnPlatform()).isFalse();
        assertThat(view.status()).isEqualTo(FacilityLegitimacyStatus.EXPIRED);

        // Previous values ride on the outbox event so the overwrite stays traceable.
        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        EventOutboxEntity event = outboxCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(FacilitySourceLegitimacyService.EVENT_UPSERTED);
        @SuppressWarnings("unchecked")
        var previous = (java.util.Map<String, Object>) event.getPayload().get("previous");
        assertThat(previous).isNotNull();
        assertThat(previous.get("status")).isEqualTo("REGISTERED_CURRENT");
        assertThat(previous.get("allowedOnPlatform")).isEqualTo(true);
    }

    @Test
    void firstUpsertForSourceCreatesRowWithNoPreviousValues() {
        stubFacility();
        stubSaveEcho();
        when(legitimacyRepository.findByFacilityIdAndSource(facilityUuid, FacilityLegitimacySource.MINISTRY_OPERATIONAL))
                .thenReturn(Optional.empty());

        service.upsert(facilityUuid, FacilityLegitimacySource.MINISTRY_OPERATIONAL,
                new FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest(
                        FacilityLegitimacyStatus.REGISTERED_CURRENT, null, "NATIONAL_FACILITY_CODE:ZW010125",
                        true, null));

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload().get("previous")).isNull();
    }

    // ── GOVERNMENT_OPERATIONAL_EXCEPTION doctrine validation ─────────────────────────────────

    @Test
    void governmentOperationalExceptionWithoutReasonIsRejected() {
        stubFacility();

        assertThatThrownBy(() -> service.upsert(facilityUuid, FacilityLegitimacySource.HPA_LEGAL,
                new FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest(
                        FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION, Instant.now(),
                        null, true, "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(legitimacyRepository, never()).save(any());
    }

    @Test
    void governmentOperationalExceptionWithoutExplicitAllowedOnPlatformIsRejected() {
        stubFacility();

        assertThatThrownBy(() -> service.upsert(facilityUuid, FacilityLegitimacySource.HPA_LEGAL,
                new FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest(
                        FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION, Instant.now(),
                        null, null, "Ministry-run facility continues operating during HPA renewal")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(legitimacyRepository, never()).save(any());
    }

    // ── Composite platform-access derivation ─────────────────────────────────────────────────

    @Test
    void compositeDeniesWhenAnySourceDeniesEvenIfAnotherAllows() {
        stubFacility();
        when(certificateRepository.findByFacilityIdOrderByIssueDateDesc(42L)).thenReturn(List.of());
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of(
                row(FacilityLegitimacySource.MINISTRY_OPERATIONAL,
                        FacilityLegitimacyStatus.REGISTERED_CURRENT, true, null),
                row(FacilityLegitimacySource.HPA_LEGAL,
                        FacilityLegitimacyStatus.SUSPENDED, false, "Suspended by HPA enforcement")));

        var composite = service.getComposite(facilityUuid);

        assertThat(composite.platformAccessAllowed()).isFalse();
        assertThat(composite.regulatoryStatus()).isEqualTo(FacilityRegulatoryStatus.REGISTERED_ACTIVE);
        assertThat(composite.reasons()).anySatisfy(r ->
                assertThat(r).contains("HPA_LEGAL").contains("SUSPENDED").contains("denies"));
    }

    @Test
    void compositeDeniesWhenNoSourceHasRecordedAVerdict() {
        stubFacility();
        when(certificateRepository.findByFacilityIdOrderByIssueDateDesc(42L)).thenReturn(List.of());
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of());

        var composite = service.getComposite(facilityUuid);

        // Silence never grants: no rows -> no platform access.
        assertThat(composite.platformAccessAllowed()).isFalse();
        assertThat(composite.reasons()).anySatisfy(r ->
                assertThat(r).contains("No source legitimacy recorded"));
    }

    @Test
    void compositeAllowsWhenEverySourceAllowsAndAtLeastOneExists() {
        stubFacility();
        when(certificateRepository.findByFacilityIdOrderByIssueDateDesc(42L)).thenReturn(List.of());
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of(
                row(FacilityLegitimacySource.MINISTRY_OPERATIONAL,
                        FacilityLegitimacyStatus.REGISTERED_CURRENT, true, null)));

        assertThat(service.getComposite(facilityUuid).platformAccessAllowed()).isTrue();
    }

    /**
     * Doctrine example (honest per-source truth): a public facility is Ministry-known and
     * operating while its HPA registration is EXPIRED. The expired HPA verdict blocks platform
     * access until governance records a GOVERNMENT_OPERATIONAL_EXCEPTION with an explicit reason
     * and an explicit allowed-on-platform decision — then the facility operates under the
     * exception, with the reason visible in the composite.
     */
    @Test
    void doctrineExample_ministryKnownHpaExpiredPublicFacility_operatesUnderExplicitException() {
        stubFacility();
        stubSaveEcho();
        when(certificateRepository.findByFacilityIdOrderByIssueDateDesc(42L)).thenReturn(List.of());

        FacilitySourceLegitimacyEntity ministry = row(FacilityLegitimacySource.MINISTRY_OPERATIONAL,
                FacilityLegitimacyStatus.REGISTERED_CURRENT, true, null);
        FacilitySourceLegitimacyEntity hpaExpired = row(FacilityLegitimacySource.HPA_LEGAL,
                FacilityLegitimacyStatus.EXPIRED, false, "HPA registration lapsed 2025-12-31");

        // Before the exception: HPA's expired verdict denies platform operation.
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(ministry, hpaExpired));
        assertThat(service.getComposite(facilityUuid).platformAccessAllowed()).isFalse();

        // Governance records the doctrine exception — reason AND explicit allow are mandatory.
        when(legitimacyRepository.findByFacilityIdAndSource(facilityUuid, FacilityLegitimacySource.HPA_LEGAL))
                .thenReturn(Optional.of(hpaExpired));
        var view = service.upsert(facilityUuid, FacilityLegitimacySource.HPA_LEGAL,
                new FacilitySourceLegitimacyDtos.UpsertSourceLegitimacyRequest(
                        FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION,
                        Instant.parse("2026-07-01T00:00:00Z"),
                        "MOHCC-DIRECTIVE-2026-014",
                        true,
                        "Public facility operating under Ministry authority while HPA renewal is processed"));
        assertThat(view.status()).isEqualTo(FacilityLegitimacyStatus.GOVERNMENT_OPERATIONAL_EXCEPTION);
        assertThat(view.allowedOnPlatform()).isTrue();

        // After the exception: same (facility, source) row overwritten -> operating is allowed,
        // and the exception reason is surfaced honestly in the composite.
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(ministry, hpaExpired));
        var composite = service.getComposite(facilityUuid);
        assertThat(composite.platformAccessAllowed()).isTrue();
        assertThat(composite.reasons()).anySatisfy(r -> assertThat(r)
                .contains("GOVERNMENT_OPERATIONAL_EXCEPTION")
                .contains("Public facility operating under Ministry authority"));
    }

    @Test
    void tenantIsolationIsEnforcedOnEveryRead() {
        FacilityEntity foreign = new FacilityEntity();
        foreign.setId(43L);
        foreign.setFacilityUuid(facilityUuid);
        foreign.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getComposite(facilityUuid))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void unknownFacilityIs404() {
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSourceLegitimacy(facilityUuid))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── FCV-W0b: the rule is tri-state, so gates and reporting can both be right ─────────────

    @Test
    void verdictOf_distinguishesNoVerdictFromDeny() {
        // The distinction the fourth copy of this rule depended on: a gate must refuse both, but
        // trust reporting must not tell an operator their facility FAILED a check nobody has run.
        assertThat(FacilitySourceLegitimacyService.verdictOf(List.of()))
                .isEqualTo(FacilitySourceLegitimacyService.PlatformAccessVerdict.NO_VERDICT);

        FacilitySourceLegitimacyEntity denies = new FacilitySourceLegitimacyEntity();
        denies.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        denies.setStatus(FacilityLegitimacyStatus.PENDING_VERIFICATION);
        denies.setAllowedOnPlatform(false);

        FacilitySourceLegitimacyEntity allows = new FacilitySourceLegitimacyEntity();
        allows.setSource(FacilityLegitimacySource.MINISTRY_OPERATIONAL);
        allows.setStatus(FacilityLegitimacyStatus.REGISTERED_CURRENT);
        allows.setAllowedOnPlatform(true);

        assertThat(FacilitySourceLegitimacyService.verdictOf(List.of(denies)))
                .isEqualTo(FacilitySourceLegitimacyService.PlatformAccessVerdict.DENY);
        assertThat(FacilitySourceLegitimacyService.verdictOf(List.of(allows)))
                .isEqualTo(FacilitySourceLegitimacyService.PlatformAccessVerdict.ALLOW);
        // One deny outranks any number of allows — a Ministry recognition cannot undercut the
        // platform's "not verified", and a suspension cannot be undercut by a regulator listing.
        assertThat(FacilitySourceLegitimacyService.verdictOf(List.of(allows, denies)))
                .isEqualTo(FacilitySourceLegitimacyService.PlatformAccessVerdict.DENY);
    }

    @Test
    void platformAccessAllowed_stillCollapsesNoVerdictToRefusal() {
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of());
        assertThat(service.platformAccessAllowed(facilityUuid, null))
                .as("silence never grants — the boolean contract every gate relies on is unchanged")
                .isFalse();
    }

    // ── FCV-W0a: an import may not silently revoke a governed Ministry decision ──────────────

    @Test
    void stampFromImport_refusesToOverwriteADecisionBackedRow() {
        // A re-import would otherwise re-stamp PENDING_VERIFICATION/false over a Ministry verdict
        // and take the facility dark with no error — stampFromImport swallows exceptions by design,
        // so nothing would surface it.
        FacilitySourceLegitimacyEntity decisionBacked = new FacilitySourceLegitimacyEntity();
        decisionBacked.setId(7L);
        decisionBacked.setFacilityId(facilityUuid);
        decisionBacked.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        decisionBacked.setStatus(FacilityLegitimacyStatus.REGISTERED_CURRENT);
        decisionBacked.setAllowedOnPlatform(true);
        decisionBacked.setDecisionId(UUID.randomUUID());
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(decisionBacked));

        service.stampFromImport(facility, FacilityLegitimacySource.PLATFORM_OPERATIONAL,
                FacilityLegitimacyStatus.PENDING_VERIFICATION, false,
                "MASTER_PACK", "re-import", "importer", tenantId, "corr-1");

        verify(legitimacyRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        assertThat(decisionBacked.isAllowedOnPlatform())
                .as("the Ministry verdict must survive an import touching the same facility")
                .isTrue();
    }

    @Test
    void stampFromImport_stillWritesAnUnboundRow() {
        FacilitySourceLegitimacyEntity unbound = new FacilitySourceLegitimacyEntity();
        unbound.setId(8L);
        unbound.setFacilityId(facilityUuid);
        unbound.setSource(FacilityLegitimacySource.PLATFORM_OPERATIONAL);
        unbound.setAllowedOnPlatform(false);
        when(legitimacyRepository.findByFacilityIdAndSource(
                facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL))
                .thenReturn(Optional.of(unbound));
        when(legitimacyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.stampFromImport(facility, FacilityLegitimacySource.PLATFORM_OPERATIONAL,
                FacilityLegitimacyStatus.PENDING_VERIFICATION, false,
                "MASTER_PACK", "import", "importer", tenantId, "corr-2");

        verify(legitimacyRepository).save(any());
    }
}
