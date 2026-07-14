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
import zw.gov.mohcc.impilo.coverage.domain.SubsidyDrawdownEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyBalanceRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyDrawdownRepository;
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
    @Mock private SubsidyDrawdownRepository drawdownRepository;

    private SubsidyEnrolmentService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubsidyEnrolmentService(
                programRepository, enrolmentRepository, balanceRepository, drawdownRepository);
        lenient().when(enrolmentRepository.save(any(SubsidyEnrolmentEntity.class))).thenAnswer(inv -> {
            SubsidyEnrolmentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getEffectiveFrom() == null) e.setEffectiveFrom(LocalDate.now());
            return e;
        });
        lenient().when(balanceRepository.save(any(SubsidyBalanceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(balanceRepository.saveAndFlush(any(SubsidyBalanceEntity.class)))
                .thenAnswer(inv -> {
                    SubsidyBalanceEntity b = inv.getArgument(0);
                    if (b.getId() == null) b.setId(UUID.randomUUID());
                    return b;
                });
        lenient().when(drawdownRepository.findByEnrolmentIdAndReference(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(drawdownRepository.saveAndFlush(any(SubsidyDrawdownEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
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
                null, "SUB-TEST", "CPID-1", null, null, null, "admin", null));

        assertEquals("ACTIVE", r.status());
        assertEquals(0, new BigDecimal("1500.00").compareTo(r.effectiveCap()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(r.remainingAmount()));
        assertNull(r.exemptionCategory());
    }

    @Test
    void enrol_withExemptionCategory_persistsNormalised() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(false);

        SubsidyEnrolmentResponse r = service.enrol(tenantId, new SubsidyEnrolmentRequest(
                null, "SUB-TEST", "CPID-1", null, "  indigent ", null, "admin",
                LocalDate.of(2026, 1, 15)));

        assertEquals("INDIGENT", r.exemptionCategory());
        assertEquals(LocalDate.of(2026, 1, 15), r.effectiveFrom());
        verify(enrolmentRepository).save(argThat(e -> "INDIGENT".equals(e.getExemptionCategory())));
    }

    @Test
    void enrol_blankExemptionCategory_leavesNull() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(false);

        SubsidyEnrolmentResponse r = service.enrol(tenantId, new SubsidyEnrolmentRequest(
                null, "SUB-TEST", "CPID-1", null, "   ", null, "admin", null));

        assertNull(r.exemptionCategory());
        verify(enrolmentRepository).save(argThat(e -> e.getExemptionCategory() == null));
    }

    @Test
    void enrol_duplicateActive_isRejected() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(true);

        var req = new SubsidyEnrolmentRequest(null, "SUB-TEST", "CPID-1", null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.enrol(tenantId, req));
    }

    @Test
    void enrol_inactiveProgram_isRejected() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ENDED")));
        var req = new SubsidyEnrolmentRequest(null, "SUB-TEST", "CPID-1", null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.enrol(tenantId, req));
    }

    @Test
    void consume_withinCap_succeeds() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));

        // Mutable balance the atomic update drives. ensureBalance creates it (consumed 0);
        // applyDrawdown bumps consumed and reports success; subsequent reads see the new total.
        SubsidyBalanceEntity bal = new SubsidyBalanceEntity();
        bal.setId(UUID.randomUUID());
        bal.setEnrolmentId(enrolId);
        bal.setConsumedAmount(BigDecimal.ZERO);
        bal.setCapAmount(new BigDecimal("1000.00"));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt()))
                .thenReturn(Optional.of(bal));
        when(balanceRepository.applyDrawdown(eq(bal.getId()), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    BigDecimal amt = inv.getArgument(1);
                    BigDecimal next = bal.getConsumedAmount().add(amt);
                    if (next.compareTo(bal.getCapAmount()) > 0) return 0;
                    bal.setConsumedAmount(next);
                    return 1;
                });

        SubsidyEnrolmentResponse r = service.consume(tenantId, enrolId,
                new SubsidyConsumeRequest(new BigDecimal("300.00"), "BILL-1"));

        assertEquals(0, new BigDecimal("300.00").compareTo(r.consumedAmount()));
        assertEquals(0, new BigDecimal("700.00").compareTo(r.remainingAmount()));
        verify(balanceRepository).applyDrawdown(eq(bal.getId()), eq(new BigDecimal("300.00")));
        verify(drawdownRepository).saveAndFlush(any(SubsidyDrawdownEntity.class));
    }

    @Test
    void consume_exceedingCap_isRejected() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        SubsidyBalanceEntity bal = new SubsidyBalanceEntity();
        bal.setId(UUID.randomUUID());
        bal.setEnrolmentId(enrolId);
        bal.setConsumedAmount(new BigDecimal("900.00"));
        bal.setCapAmount(new BigDecimal("1000.00"));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt())).thenReturn(Optional.of(bal));
        // Atomic update rejects the over-cap drawdown (rowcount 0).
        when(balanceRepository.applyDrawdown(eq(bal.getId()), any(BigDecimal.class))).thenReturn(0);

        var req = new SubsidyConsumeRequest(new BigDecimal("200.00"), null);
        assertThrows(IllegalArgumentException.class, () -> service.consume(tenantId, enrolId, req));
        verify(drawdownRepository, never()).saveAndFlush(any());
    }

    /**
     * H2 regression: a retried consume with the same reference returns the prior result and
     * never draws down again. BEFORE the fix reference was ignored and the subsidy was drawn
     * down twice.
     */
    @Test
    void consume_sameReferenceTwice_isIdempotent() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));

        // First call applies; record the ledger row keyed by reference.
        SubsidyBalanceEntity bal = new SubsidyBalanceEntity();
        bal.setId(UUID.randomUUID());
        bal.setEnrolmentId(enrolId);
        bal.setConsumedAmount(BigDecimal.ZERO);
        bal.setCapAmount(new BigDecimal("1000.00"));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt()))
                .thenReturn(Optional.of(bal));
        when(balanceRepository.applyDrawdown(eq(bal.getId()), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    bal.setConsumedAmount(bal.getConsumedAmount().add(inv.getArgument(1)));
                    return 1;
                });
        java.util.Map<String, SubsidyDrawdownEntity> ledger = new java.util.HashMap<>();
        when(drawdownRepository.findByEnrolmentIdAndReference(eq(enrolId), eq("BILL-9")))
                .thenAnswer(inv -> Optional.ofNullable(ledger.get("BILL-9")));
        when(drawdownRepository.saveAndFlush(any(SubsidyDrawdownEntity.class)))
                .thenAnswer(inv -> { SubsidyDrawdownEntity d = inv.getArgument(0); ledger.put(d.getReference(), d); return d; });

        var req = new SubsidyConsumeRequest(new BigDecimal("400.00"), "BILL-9");
        SubsidyEnrolmentResponse first = service.consume(tenantId, enrolId, req);
        SubsidyEnrolmentResponse second = service.consume(tenantId, enrolId, req);

        assertEquals(0, new BigDecimal("400.00").compareTo(first.consumedAmount()));
        assertEquals(0, new BigDecimal("400.00").compareTo(second.consumedAmount()));
        // Drawdown applied exactly once across both calls.
        verify(balanceRepository, times(1)).applyDrawdown(eq(bal.getId()), eq(new BigDecimal("400.00")));
        verify(drawdownRepository, times(1)).saveAndFlush(any(SubsidyDrawdownEntity.class));
    }

    @Test
    void enrol_negativeCapOverride_isRejected() {
        when(programRepository.findByTenantIdAndProgramCode(tenantId, "SUB-TEST"))
                .thenReturn(Optional.of(program(new BigDecimal("1500.00"), "ACTIVE")));
        when(enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, programId, "CPID-1", "ACTIVE")).thenReturn(false);
        var req = new SubsidyEnrolmentRequest(
                null, "SUB-TEST", "CPID-1", new BigDecimal("-1.00"), null, null, "admin", null);
        assertThrows(IllegalArgumentException.class, () -> service.enrol(tenantId, req));
    }

    @Test
    void end_activeEnrolment_setsEndedAndEffectiveTo() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt()))
                .thenReturn(Optional.empty());

        SubsidyEnrolmentResponse r = service.end(tenantId, enrolId);

        assertEquals("ENDED", r.status());
        assertEquals(LocalDate.now(), r.effectiveTo());
        verify(enrolmentRepository).save(e);
    }

    @Test
    void end_alreadyEnded_isIdempotent() {
        UUID enrolId = UUID.randomUUID();
        SubsidyEnrolmentEntity e = enrolment(enrolId);
        e.setStatus("ENDED");
        LocalDate endedOn = LocalDate.now().minusDays(3);
        e.setEffectiveTo(endedOn);
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.of(e));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(eq(enrolId), anyInt()))
                .thenReturn(Optional.empty());

        SubsidyEnrolmentResponse r = service.end(tenantId, enrolId);

        assertEquals("ENDED", r.status());
        assertEquals(endedOn, r.effectiveTo());  // original end date kept, not re-stamped
        verify(enrolmentRepository, never()).save(any());
    }

    @Test
    void end_unknownEnrolment_isRejected() {
        UUID enrolId = UUID.randomUUID();
        when(enrolmentRepository.findByIdAndTenantId(enrolId, tenantId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.end(tenantId, enrolId));
    }

    @Test
    void listForMember_exemptionOnly_filtersValueOnlyRows() {
        SubsidyEnrolmentEntity valueOnly = enrolment(UUID.randomUUID());
        SubsidyEnrolmentEntity exempt = enrolment(UUID.randomUUID());
        exempt.setExemptionCategory("INDIGENT");
        when(enrolmentRepository.findByTenantIdAndMemberCpid(tenantId, "CPID-1"))
                .thenReturn(List.of(valueOnly, exempt));
        when(programRepository.findByIdAndTenantId(programId, tenantId))
                .thenReturn(Optional.of(program(new BigDecimal("1000.00"), "ACTIVE")));
        when(balanceRepository.findByEnrolmentIdAndPeriodYear(any(), anyInt()))
                .thenReturn(Optional.empty());

        List<SubsidyEnrolmentResponse> all = service.listForMember(tenantId, "CPID-1", false);
        List<SubsidyEnrolmentResponse> exemptOnly = service.listForMember(tenantId, "CPID-1", true);

        assertEquals(2, all.size());
        assertEquals(1, exemptOnly.size());
        assertEquals("INDIGENT", exemptOnly.get(0).exemptionCategory());
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
