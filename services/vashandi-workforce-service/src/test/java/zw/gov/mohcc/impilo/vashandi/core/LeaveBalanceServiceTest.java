package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveBalanceEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveBalanceRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    @Mock LeaveAvailabilityRepository leaveRepository;
    @Mock LeaveBalanceRepository balanceRepository;
    @Mock VashandiOutboxWriter outboxWriter;

    private VashandiDtos.CreateLeaveRequest request(UUID profile, String type, LocalDate from, LocalDate to) {
        return new VashandiDtos.CreateLeaveRequest(profile, type, "pending", from, to, "self");
    }

    @Test
    void reservesBalanceWhenConfigured() throws Exception {
        LeaveAvailabilityService svc = new LeaveAvailabilityService(leaveRepository, balanceRepository, outboxWriter);
        UUID profile = UUID.randomUUID();
        LeaveBalanceEntity bal = new LeaveBalanceEntity();
        bal.setTenantId(TENANT);
        bal.setWorkforceProfileId(profile);
        bal.setLeaveType("ANNUAL");
        bal.setFiscalYear(2026);
        bal.setEntitlement(10);
        bal.setUsed(0);
        bal.setAvailable(10);
        when(balanceRepository.findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
                TENANT, profile, "ANNUAL", 2026)).thenReturn(Optional.of(bal));
        when(leaveRepository.save(any())).thenAnswer(i -> {
            LeaveAvailabilityEntity l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        svc.create(TENANT, request(profile, "ANNUAL", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4)));

        assertThat(bal.getUsed()).isEqualTo(3);
        assertThat(bal.getAvailable()).isEqualTo(7);
        verify(balanceRepository).save(bal);
    }

    @Test
    void rejectsWhenInsufficient() {
        LeaveAvailabilityService svc = new LeaveAvailabilityService(leaveRepository, balanceRepository, outboxWriter);
        UUID profile = UUID.randomUUID();
        LeaveBalanceEntity bal = new LeaveBalanceEntity();
        bal.setAvailable(2);
        when(balanceRepository.findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
                TENANT, profile, "ANNUAL", 2026)).thenReturn(Optional.of(bal));

        assertThatThrownBy(() -> svc.create(TENANT,
                request(profile, "ANNUAL", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
        verify(leaveRepository, never()).save(any());
    }

    @Test
    void allowsWhenNoBalanceConfigured() throws Exception {
        LeaveAvailabilityService svc = new LeaveAvailabilityService(leaveRepository, balanceRepository, outboxWriter);
        UUID profile = UUID.randomUUID();
        when(balanceRepository.findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
                any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(leaveRepository.save(any())).thenAnswer(i -> {
            LeaveAvailabilityEntity l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        LeaveAvailabilityEntity saved = svc.create(TENANT,
                request(profile, "UNPAID", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2)));

        assertThat(saved).isNotNull();
        verify(balanceRepository, never()).save(any());
    }
}
