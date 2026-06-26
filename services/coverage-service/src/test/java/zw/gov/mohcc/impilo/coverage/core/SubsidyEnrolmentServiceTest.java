package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyConsumeRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyBalanceEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyBalanceRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubsidyEnrolmentServiceTest {

    @Mock private SubsidyProgramRepository programRepository;
    @Mock private SubsidyEnrolmentRepository enrolmentRepository;
    @Mock private SubsidyBalanceRepository balanceRepository;

    private SubsidyEnrolmentService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubsidyEnrolmentService(programRepository, enrolmentRepository, balanceRepository);
        lenient().when(enrolmentRepository.save(any(SubsidyEnrolmentEntity.class))).thenAnswer(inv -> {
            SubsidyEnrolmentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getEffectiveFrom() == null) e.setEffectiveFrom(LocalDate.now());
            return e;
        });
        lenient().when(balanceRepository.save(any(SubsidyBalanceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private SubsidyProgramEntity program(BigDecimal cap, String status) {
        SubsidyProgramEntity p;
        try {
            var ctor = SubsidyProgramEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            p = ctor.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        setField(p, "id", programId);
        setField(p, "tenantId", tenantId);
        setField(p, "programCode", "SUB-TEST");
        setField(p, "status", status);
        setField(p, "annualCap", cap);
        setField(p, "currency", "USD");
        return p;
    }

    @Test
    void enrol_byCode_succeeds() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(false);

        SubsidyEnrolmentResponse r = service.enrol(tenantId, new SubsidyEnrolmentRequest(
                null, "SUB-TEST", "CPID-1", null, null, "admin"));

        assertEquals("ACTIVE", r.status());
        assertEquals(0, new BigDecimal("1500.00").compareTo(r.effectiveCap()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(r.remainingAmount()));
    }

    @Test
    void enrol_duplicateActive_isRejected() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(true);

        var req = new SubsidyEnrolmentRequest(null, "SUB-TEST", "CPID-1", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.enrol(tenantId, req));
    }

    @Test
    void enrol_inactiveProgram_isRejected() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ENDED")));
        var req = new SubsidyEnrolmentRequest(null, "SUB-TEST", "CPID-1", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.enrol(tenantId, req));
    }

    @Test
    void consume_withinCap_succeeds() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt()))
                .thenReturn(Optional.empty());

        SubsidyEnrolmentResponse r = service.consume(tenantId, enrolId,
                new SubsidyConsumeRequest(new BigDecimal("300.00"), "BILL-1"));

        assertEquals(0, new BigDecimal("300.00").compareTo(r.consumedAmount()));
        assertEquals(0, new BigDecimal("700.00").compareTo(r.remainingAmount()));
        verify(balanceRepository).save(any(SubsidyBalanceEntity.class));
    }

    @Test
    void consume_exceedingCap_isRejected() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        SubsidyBalanceEntity bal = new SubsidyBalanceEntity();
        bal.setEnrolmentId(enrolId);
        bal.setConsumedAmount(new BigDecimal("900.00"));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt())).thenReturn(Optional.of(bal));

        var req = new SubsidyConsumeRequest(new BigDecimal("200.00"), null);
        assertThrows(IllegalArgumentException.class, () -> service.consume(tenantId, enrolId, req));
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void checkCap_noEnrolment_returnsNotEligible() {
        when(enrolmentRepository.findByTenantIdAndMemberCpidAndStatus(tenantId, "CPID-X", "ACTIVE"))
                .thenReturn(List.of());
        var r = service.checkCap(tenantId, "CPID-X", new BigDecimal("50.00"));
        assertFalse(r.eligible());
        assertEquals("NO_ACTIVE_SUBSIDY", r.reason());
    }

    @Test
    void checkCap_withHeadroom_returnsEligible() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByTenantIdAndMemberCpidAndStatus(tenantId, "CPID-1", "ACTIVE"))
                .thenReturn(List.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt())).thenReturn(Optional.empty());

        var r = service.checkCap(tenantId, "CPID-1", new BigDecimal("400.00"));
        assertTrue(r.eligible());
        assertEquals(0, new BigDecimal("1000.00").compareTo(r.remaining()));
    }

    @Test
    void checkCap_capExhausted_returnsNotEligible() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByTenantIdAndMemberCpidAndStatus(tenantId, "CPID-1", "ACTIVE"))
                .thenReturn(List.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        SubsidyBalanceEntity bal = new SubsidyBalanceEntity();
        bal.setConsumedAmount(new BigDecimal("980.00"));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt())).thenReturn(Optional.of(bal));

        var r = service.checkCap(tenantId, "CPID-1", new BigDecimal("50.00"));
        assertFalse(r.eligible());
        assertEquals("SUBSIDY_CAP_EXHAUSTED", r.reason());
    }

    private SubsidyEnrolmentEntity enrolment(UUID id) {
        SubsidyEnrolmentEntity e = new SubsidyEnrolmentEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setSubsidyProgramId(programId);
        e.setMemberCpid("CPID-1");
        e.setStatus("ACTIVE");
        e.setCurrency("USD");
        e.setEffectiveFrom(LocalDate.now());
        return e;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
