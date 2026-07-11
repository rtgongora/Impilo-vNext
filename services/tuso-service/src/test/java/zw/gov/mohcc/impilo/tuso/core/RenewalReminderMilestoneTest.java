package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityStatusHistoryRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renewal reminders are PROGRAMME OPERATIONAL POLICY: milestones come from
 * configuration (default 90/60/30/7), never hard-coded regulatory law, and
 * reminder emission never touches certificate validity.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RenewalReminderMilestoneTest {

    @Mock private FacilityCertificateRepository certificateRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityStatusHistoryRepository statusHistoryRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private RegulatoryRuleService ruleService;

    @Test
    void emitsOneReminderPerConfiguredMilestoneWithoutTouchingValidity() {
        RenewalDueScheduler scheduler = new RenewalDueScheduler(
                certificateRepository, facilityRepository, statusHistoryRepository, outboxRepository, ruleService);
        when(ruleService.reminderMilestoneDays()).thenReturn(List.of(90, 60, 30, 7));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(certificateRepository.findByExpiryDateBetweenAndStatus(any(), any(), eq(FacilityCertificateStatus.ACTIVE)))
                .thenReturn(List.of());

        FacilityEntity facility = new FacilityEntity();
        facility.setId(7L);
        FacilityCertificateEntity certificate = new FacilityCertificateEntity();
        certificate.setCertificateId(UUID.randomUUID());
        certificate.setCertificateNumber("HPA-2026-PROOF");
        certificate.setFacility(facility);
        certificate.setTenantId(UUID.randomUUID());
        certificate.setStatus(FacilityCertificateStatus.ACTIVE);
        certificate.setExpiryDate(LocalDate.now().plusDays(30));
        LocalDate thirtyOut = LocalDate.now().plusDays(30);
        when(certificateRepository.findByExpiryDateBetweenAndStatus(
                eq(thirtyOut), eq(thirtyOut), eq(FacilityCertificateStatus.ACTIVE)))
                .thenReturn(List.of(certificate));

        int emitted = scheduler.emitReminderMilestones();

        assertThat(emitted).isEqualTo(1);
        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(event.capture());
        EventOutboxEntity reminder = event.getValue();
        assertThat(reminder.getEventType()).isEqualTo("tuso.facility.certificate.renewal_reminder");
        assertThat(reminder.getPayload()).containsEntry("daysToExpiry", 30)
                .containsEntry("policySource", "PROGRAMME_OPERATIONAL_POLICY");
        assertThat(reminder.getPodId()).isEqualTo("national-spine");
        assertThat(reminder.getIdempotencyKey()).isNotBlank();
        // Reminder emission never mutates the certificate.
        assertThat(certificate.getStatus()).isEqualTo(FacilityCertificateStatus.ACTIVE);
        assertThat(certificate.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void milestonesFallBackToDefaultsWhenRuleAbsent() {
        RegulatoryRuleService realService = new RegulatoryRuleService(
                org.mockito.Mockito.mock(zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryRuleRepository.class));
        assertThat(realService.reminderMilestoneDays()).containsExactly(90, 60, 30, 7);
    }
}
