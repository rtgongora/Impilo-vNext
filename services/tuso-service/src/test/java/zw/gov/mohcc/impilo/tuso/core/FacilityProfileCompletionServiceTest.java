package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos.CompleteFacilityProfileRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos.GeocodeProvenance;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PractitionerInChargeAssignmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityProfileCompletionServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityGeoRepository geoRepository;
    @Mock private FacilityHistoryRepository historyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private FacilityAdminAppointmentRepository adminAppointmentRepository;
    @Mock private PractitionerInChargeAssignmentRepository picAssignmentRepository;

    private FacilityProfileCompletionService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID facilityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new FacilityProfileCompletionService(
                facilityRepository, geoRepository, historyRepository, outboxRepository,
                adminAppointmentRepository, picAssignmentRepository, new ObjectMapper());
        facility = new FacilityEntity();
        facility.setId(42L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setFacilityCode("ZW010125");
        facility.setName("Bangure Clinic");
        facility.setVersion(3);
        lenient().when(facilityRepository.findById(42L)).thenReturn(Optional.of(facility));
        lenient().when(facilityRepository.getReferenceById(anyLong())).thenReturn(facility);
        lenient().when(facilityRepository.save(any(FacilityEntity.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(geoRepository.save(any(FacilityGeoEntity.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void actAs(String actorId) {
        TrustContextHolder.set(new TrustContext(
                tenantId, actorId, "PROVIDER", "FACILITY_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    private void grantActiveAdmin(String actorId) {
        when(adminAppointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facilityUuid, actorId, FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(true);
    }

    private void grantActivePic(String providerPublicId) {
        when(adminAppointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                any(UUID.class), any(String.class), any(String.class))).thenReturn(false);
        PractitionerInChargeAssignmentEntity pic = new PractitionerInChargeAssignmentEntity();
        pic.setFacility(facility);
        pic.setProviderPublicId(providerPublicId);
        pic.setRole("PRACTITIONER_IN_CHARGE");
        pic.setStartDate(LocalDate.now().minusMonths(6));
        pic.setEndDate(null);
        pic.setApprovalState("APPROVED");
        when(picAssignmentRepository.findByFacilityIdAndEndDateIsNull(42L)).thenReturn(List.of(pic));
    }

    private static CompleteFacilityProfileRequest geocodeOnly(GeocodeProvenance provenance) {
        return new CompleteFacilityProfileRequest(
                null, null, null,
                new BigDecimal("-17.8292000"), new BigDecimal("31.0522000"),
                null, null, null, null, null, null, null, provenance);
    }

    // ── authz fail-closed ────────────────────────────────────────────────────

    @Test
    void strangerWithoutAppointmentOrPicGets403AndNothingIsWritten() {
        actAs("stranger-1");
        when(adminAppointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facilityUuid, "stranger-1", FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(false);
        when(picAssignmentRepository.findByFacilityIdAndEndDateIsNull(42L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("DEVICE_GPS", null, null, 8.0))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(historyRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(facilityRepository, never()).save(any());
    }

    @Test
    void endedPicAssignmentDoesNotAuthorize() {
        actAs("pic-old");
        when(adminAppointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facilityUuid, "pic-old", FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(false);
        // repository query already excludes ended assignments — an ended PIC yields empty
        when(picAssignmentRepository.findByFacilityIdAndEndDateIsNull(42L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("DEVICE_GPS", null, null, 8.0))))
                .isInstanceOf(ResponseStatusException.class);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void activeFacilityAdminIsAllowed() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        var result = service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("DEVICE_GPS", null, null, 8.0)));

        assertThat(facility.getLatitude()).isEqualByComparingTo("-17.8292");
        assertThat(result.geocodeStatus()).isEqualTo(FacilityCompletenessService.GEOCODE_PRESENT);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void activePicIsAllowed() {
        actAs("provider-pub-9");
        grantActivePic("provider-pub-9");

        var result = service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("ADDRESS_SEARCH", "ndila", 0.95, null)));

        assertThat(result.missingFields())
                .doesNotContain(FacilityCompletenessService.MISSING_LATITUDE,
                        FacilityCompletenessService.MISSING_LONGITUDE);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    // ── history + outbox rows ────────────────────────────────────────────────

    @Test
    void perFieldHistoryCarriesCompletenessPromptSourceAndGeocodeProvenance() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        CompleteFacilityProfileRequest request = new CompleteFacilityProfileRequest(
                "CLINIC", "GOVERNMENT", null,
                new BigDecimal("-17.8292000"), new BigDecimal("31.0522000"),
                "12 Chaminuka Rd", null, "Harare", null, null, "Ward 7", null,
                new GeocodeProvenance("ADDRESS_SEARCH", "ndila", 0.92, null));

        service.completeProfile(42L, request);

        ArgumentCaptor<FacilityHistoryEntity> history = ArgumentCaptor.forClass(FacilityHistoryEntity.class);
        verify(historyRepository, org.mockito.Mockito.times(7)).save(history.capture());
        List<FacilityHistoryEntity> rows = history.getAllValues();

        assertThat(rows).extracting(FacilityHistoryEntity::getFieldName)
                .containsExactlyInAnyOrder("facility_type", "ownership", "latitude", "longitude",
                        "address_line1", "city", "ward");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getChangeType()).isEqualTo("UPDATE");
            assertThat(r.getChangedBy()).isEqualTo("admin-hid-1");
            assertThat(r.getReason()).contains("\"source\":\"COMPLETENESS_PROMPT\"");
        });
        FacilityHistoryEntity latRow = rows.stream()
                .filter(r -> "latitude".equals(r.getFieldName())).findFirst().orElseThrow();
        assertThat(latRow.getReason())
                .contains("\"geocodeProvenance\"")
                .contains("\"source\":\"ADDRESS_SEARCH\"")
                .contains("\"provider\":\"ndila\"")
                .doesNotContain("UNVERIFIED");
        // non-geocode rows carry no provenance blob
        FacilityHistoryEntity typeRow = rows.stream()
                .filter(r -> "facility_type".equals(r.getFieldName())).findFirst().orElseThrow();
        assertThat(typeRow.getReason()).doesNotContain("geocodeProvenance");

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("tuso.facility.updated");
        assertThat(outbox.getValue().getAggregateId()).isEqualTo("42");
        assertThat(outbox.getValue().getPayload())
                .containsEntry("version", 4)
                .containsEntry("action", "UPDATED")
                .containsEntry("trigger", "COMPLETENESS_PROMPT");
        assertThat(facility.getVersion()).isEqualTo(4);
        assertThat(facility.getUpdatedBy()).isEqualTo("admin-hid-1");
    }

    @Test
    void manualGeocodeIsStampedUnverified() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        service.completeProfile(42L, geocodeOnly(new GeocodeProvenance("MANUAL", null, null, null)));

        ArgumentCaptor<FacilityHistoryEntity> history = ArgumentCaptor.forClass(FacilityHistoryEntity.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).allSatisfy(r ->
                assertThat(r.getReason()).contains("\"geocodeVerification\":\"UNVERIFIED\""));
    }

    @Test
    void lowAccuracyDeviceGeocodeIsStampedUnverified() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("DEVICE_GPS", null, null, 350.0)));

        ArgumentCaptor<FacilityHistoryEntity> history = ArgumentCaptor.forClass(FacilityHistoryEntity.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).allSatisfy(r ->
                assertThat(r.getReason()).contains("\"geocodeVerification\":\"UNVERIFIED\""));
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    void coordinatesWithoutProvenanceAreRejected() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        assertThatThrownBy(() -> service.completeProfile(42L, geocodeOnly(null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void latitudeWithoutLongitudeIsRejected() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");

        CompleteFacilityProfileRequest halfGeocode = new CompleteFacilityProfileRequest(
                null, null, null, new BigDecimal("-17.8"), null,
                null, null, null, null, null, null, null,
                new GeocodeProvenance("DEVICE_GPS", null, null, 5.0));

        assertThatThrownBy(() -> service.completeProfile(42L, halfGeocode))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void crossTenantWriteIsRejected() {
        actAs("admin-hid-1");
        facility.setTenantId(UUID.randomUUID());

        assertThatThrownBy(() -> service.completeProfile(42L,
                geocodeOnly(new GeocodeProvenance("DEVICE_GPS", null, null, 5.0))))
                .isInstanceOf(SecurityException.class);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void noEffectiveChangeSkipsVersionBumpAndOutbox() {
        actAs("admin-hid-1");
        grantActiveAdmin("admin-hid-1");
        facility.setFacilityType("CLINIC");

        CompleteFacilityProfileRequest sameType = new CompleteFacilityProfileRequest(
                "CLINIC", null, null, null, null,
                null, null, null, null, null, null, null, null);
        service.completeProfile(42L, sameType);

        verify(historyRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        assertThat(facility.getVersion()).isEqualTo(3);
    }
}
