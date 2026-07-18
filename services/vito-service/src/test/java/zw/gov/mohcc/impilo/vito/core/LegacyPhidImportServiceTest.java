package zw.gov.mohcc.impilo.vito.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationResponse;
import zw.gov.mohcc.impilo.vito.core.id.LegacyPhidFormat;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.LegacyPhidImportEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.LegacyPhidImportRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.PhidPoolRepository;
import zw.gov.mohcc.impilo.vito.service.ExternalRegistrationService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Identity Contract §5.2, §14 / E2 — bulk legacy-PHID import routing:
 * assigned → LEGACY_PHID alias, unassigned → RESERVED pool (never alias),
 * conflict → QUARANTINED, invalid → skipped. Real {@link LegacyPhidFormat}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegacyPhidImportService")
class LegacyPhidImportServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private ExternalRegistrationService externalRegistration;
    @Mock private ImpiloIdAliasService aliasService;
    @Mock private ClientIdentifierRepository clientIdentifierRepository;
    @Mock private PhidPoolRepository phidPoolRepository;
    @Mock private LegacyPhidImportRepository importRepository;

    private LegacyPhidImportService service;

    private String validPhid; // generated to pass the real format+check-digit

    @BeforeEach
    void setUp() {
        LegacyPhidFormat fmt = new LegacyPhidFormat();
        validPhid = fmt.generate();
        service = new LegacyPhidImportService(externalRegistration, aliasService,
                clientIdentifierRepository, phidPoolRepository, importRepository, fmt);
        lenient().when(importRepository.save(any(LegacyPhidImportEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private LegacyPhidImportService.ImportRecord assigned(String phid) {
        return new LegacyPhidImportService.ImportRecord(phid, "FAC-1", LocalDate.now(),
                "Thandiwe", "Moyo", "F", LocalDate.of(1990, 1, 1), null);
    }

    private LegacyPhidImportService.ImportRecord poolNumber(String phid) {
        return new LegacyPhidImportService.ImportRecord(phid, "FAC-1", LocalDate.now(),
                null, null, null, null, null);
    }

    @Test
    @DisplayName("assigned PHID → mints/links person and issues a LEGACY_PHID alias")
    void assigned_issuesLegacyAlias() {
        when(clientIdentifierRepository.findByTenantIdAndIdentifierTypeAndIdentifierValue(
                eq(TENANT), eq("MRS_PHID"), anyString())).thenReturn(Optional.empty());
        when(externalRegistration.register(eq(TENANT), any(), any(), eq("SYSTEM"), any()))
                .thenReturn(new ExternalClientRegistrationResponse(HID, null, "REGISTERED", "CREATED", "c"));

        var result = service.importBatch(TENANT, "MRS", List.of(assigned(validPhid)), "op");

        assertEquals(1, result.assigned());
        verify(aliasService).issueAlias(TENANT, HID, "LEGACY_PHID", validPhid.toUpperCase());
        verify(phidPoolRepository, never()).save(any());
    }

    @Test
    @DisplayName("unassigned pool number → RESERVED, never a client alias")
    void poolNumber_reservedNotAliased() {
        when(clientIdentifierRepository.findByTenantIdAndIdentifierTypeAndIdentifierValue(
                eq(TENANT), eq("MRS_PHID"), anyString())).thenReturn(Optional.empty());
        when(phidPoolRepository.findByPhid(anyString())).thenReturn(Optional.empty());

        var result = service.importBatch(TENANT, "MRS", List.of(poolNumber(validPhid)), "op");

        assertEquals(1, result.reserved());
        ArgumentCaptor<PhidPoolEntity> captor = ArgumentCaptor.forClass(PhidPoolEntity.class);
        verify(phidPoolRepository).save(captor.capture());
        assertEquals("RESERVED", captor.getValue().getStatus());
        verify(aliasService, never()).issueAlias(any(), any(), eq("LEGACY_PHID"), any());
        verify(externalRegistration, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("same PHID already owned by a different person → QUARANTINED, no alias")
    void conflict_quarantined() {
        UUID otherPerson = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        ClientIdentifierEntity owned = new ClientIdentifierEntity();
        owned.setClientHealthId(otherPerson);
        when(clientIdentifierRepository.findByTenantIdAndIdentifierTypeAndIdentifierValue(
                eq(TENANT), eq("MRS_PHID"), anyString())).thenReturn(Optional.of(owned));
        when(externalRegistration.register(eq(TENANT), any(), any(), eq("SYSTEM"), any()))
                .thenReturn(new ExternalClientRegistrationResponse(HID, null, "REGISTERED", "CREATED", "c"));

        var result = service.importBatch(TENANT, "MRS", List.of(assigned(validPhid)), "op");

        assertEquals(1, result.quarantined());
        verify(aliasService, never()).issueAlias(any(), any(), eq("LEGACY_PHID"), any());
    }

    @Test
    @DisplayName("invalid-format PHID is skipped as INVALID_FORMAT")
    void invalidFormat_skipped() {
        var result = service.importBatch(TENANT, "MRS", List.of(assigned("BADPHID")), "op");

        assertEquals(1, result.invalid());
        assertEquals(0, result.assigned());
        verify(externalRegistration, never()).register(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("registration request carries the PHID as mrsPhid + source system")
    void registrationRequestShape() {
        when(clientIdentifierRepository.findByTenantIdAndIdentifierTypeAndIdentifierValue(
                eq(TENANT), eq("MRS_PHID"), anyString())).thenReturn(Optional.empty());
        when(externalRegistration.register(eq(TENANT), any(), any(), eq("SYSTEM"), any()))
                .thenReturn(new ExternalClientRegistrationResponse(HID, null, "REGISTERED", "CREATED", "c"));

        service.importBatch(TENANT, "MRS-CENTRAL", List.of(assigned(validPhid)), "op");

        ArgumentCaptor<ExternalClientRegistrationRequest> captor =
                ArgumentCaptor.forClass(ExternalClientRegistrationRequest.class);
        verify(externalRegistration).register(eq(TENANT), captor.capture(), any(), eq("SYSTEM"), any());
        assertEquals(validPhid.toUpperCase(), captor.getValue().mrsPhid());
        assertEquals("MRS-CENTRAL", captor.getValue().sourceSystem());
    }
}
