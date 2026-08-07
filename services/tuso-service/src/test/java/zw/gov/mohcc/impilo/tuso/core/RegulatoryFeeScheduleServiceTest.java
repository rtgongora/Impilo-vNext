package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryFeeScheduleRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulatoryFeeScheduleServiceTest {
    // Actor types here were HPA_ADMIN / HPA_REGISTRAR / DEVELOPER / FACILITY_APPLICANT — role names
    // no client emits, so this suite was exercising the service with values no real request can
    // carry, and would have kept passing against a gate no real operator could satisfy. See
    // ActorTypeGuard. OPERATOR is what the admin consoles actually emit.

    @Mock
    private RegulatoryFeeScheduleRepository repository;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private RegulatoryFeeScheduleService service() {
        return new RegulatoryFeeScheduleService(repository);
    }

    private void ctx(String actorType) {
        TrustContextHolder.set(new TrustContext(tenantId, "tester", actorType, "REGULATION", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() { TrustContextHolder.clear(); }

    private RegulatoryFeeScheduleEntity row(BigDecimal amount, String classCode) {
        RegulatoryFeeScheduleEntity e = new RegulatoryFeeScheduleEntity();
        e.setFeeId(UUID.randomUUID());
        e.setScheduleCode("SI_78_2017");
        e.setFeeCode("FEE_INITIAL_REGISTRATION_PRIVATE");
        e.setAppliesToCatalogueCode("INITIAL_REGISTRATION_PRIVATE");
        e.setAppliesToClassCode(classCode);
        e.setAmount(amount);
        e.setCurrency("USD");
        e.setSourceInstrument("SI 78 of 2017");
        e.setStatus("ACTIVE");
        return e;
    }

    @Test
    void resolveIsConfiguredWhenAnActiveRowCarriesAnAmount() {
        when(repository.findActiveForResolution(eq("INITIAL_REGISTRATION_PRIVATE"), any()))
                .thenReturn(List.of(row(new BigDecimal("120.00"), "A")));
        var r = service().resolve("INITIAL_REGISTRATION_PRIVATE", "A");
        assertThat(r.kind()).isEqualTo(RegulatoryFeeScheduleService.Kind.CONFIGURED);
        assertThat(r.amount()).isEqualByComparingTo("120.00");
        assertThat(r.sourceInstrument()).isEqualTo("SI 78 of 2017");
    }

    @Test
    void resolveIsNotConfiguredWhenTheActiveRowHasNoAmount() {
        // The seeded structure row: ACTIVE eventually, but amount NULL until SI 78 is configured.
        when(repository.findActiveForResolution(eq("INITIAL_REGISTRATION_PRIVATE"), any()))
                .thenReturn(List.of(row(null, null)));
        var r = service().resolve("INITIAL_REGISTRATION_PRIVATE", "A");
        assertThat(r.kind()).isEqualTo(RegulatoryFeeScheduleService.Kind.NOT_CONFIGURED);
        assertThat(r.amount()).isNull();   // never invented
    }

    @Test
    void resolveIsNotConfiguredWhenNoActiveRowExists() {
        when(repository.findActiveForResolution(any(), any())).thenReturn(List.of());
        assertThat(service().resolve("RENEWAL", "C").kind())
                .isEqualTo(RegulatoryFeeScheduleService.Kind.NOT_CONFIGURED);
    }

    @Test
    void createRequiresAFeeAdminActorType() {
        ctx("FACILITY_APPLICANT");
        assertThatThrownBy(() -> service().create("SI_78_2017", "FEE_RENEWAL", "RENEWAL", null,
                new BigDecimal("50"), "USD", "SI 78 of 2017", null, 1, "n"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void approveActivatesAndStampsProvenance() {
        ctx("OPERATOR");
        RegulatoryFeeScheduleEntity pending = row(new BigDecimal("80.00"), null);
        pending.setStatus("PENDING_REGULATOR_APPROVAL");
        when(repository.findById(pending.getFeeId())).thenReturn(java.util.Optional.of(pending));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var out = service().approve(pending.getFeeId(), null);
        assertThat(out.getStatus()).isEqualTo("ACTIVE");
        assertThat(out.getApprovedBy()).isEqualTo("tester");
        assertThat(out.getEffectiveFrom()).isNotNull();
    }
}
