package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.simba.core.CareLinkageService;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;
import zw.gov.mohcc.impilo.simba.social.core.SocialModerationService;
import zw.gov.mohcc.impilo.simba.social.core.SocialNotificationService;
import zw.gov.mohcc.impilo.simba.social.entity.ContentReportEntity;
import zw.gov.mohcc.impilo.simba.social.repository.ContentReportRepository;
import zw.gov.mohcc.impilo.simba.social.repository.ModerationActionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SELF_HARM/ABUSE reports MUST build a care linkage; spam/abuse-of-others must not falsely escalate. */
class SocialModerationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SUBJECT = UUID.randomUUID();

    private SocialModerationService svc(CareLinkageService careLinkage, ContentReportRepository reports) {
        return new SocialModerationService(reports, mock(ModerationActionRepository.class),
                careLinkage, mock(SimbaEventEmitter.class), mock(SocialNotificationService.class));
    }

    @Test
    void selfHarmReport_routesEmergencyCareLinkageAndStampsId() {
        CareLinkageService careLinkage = mock(CareLinkageService.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        when(reports.save(any(ContentReportEntity.class))).thenAnswer(i -> {
            ContentReportEntity e = i.getArgument(0);
            if (e.getReportId() == null) { e.setReportId(UUID.randomUUID()); }
            return e;
        });

        UUID linkageId = UUID.randomUUID();
        when(careLinkage.route(any(CareLinkageEntity.class))).thenAnswer(i -> {
            CareLinkageEntity e = i.getArgument(0);
            e.setLinkageId(linkageId);
            return e;
        });

        ContentReportEntity r = svc(careLinkage, reports)
                .report(TENANT, "reporter", "POST", SUBJECT, "content-owner", "SELF_HARM", "worried");

        ArgumentCaptor<CareLinkageEntity> captor = ArgumentCaptor.forClass(CareLinkageEntity.class);
        verify(careLinkage).route(captor.capture());
        CareLinkageEntity routed = captor.getValue();
        assertThat(routed.getPersonCpid()).isEqualTo("content-owner");
        assertThat(routed.getSeverity()).isEqualTo("EMERGENCY");
        assertThat(routed.getTrigger()).isEqualTo("RED_FLAG");
        assertThat(r.getCareLinkageId()).isEqualTo(linkageId);
        assertThat(r.getStatus()).isEqualTo("TRIAGED");
    }

    @Test
    void abuseReport_routesUrgentCareLinkage() {
        CareLinkageService careLinkage = mock(CareLinkageService.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        when(reports.save(any(ContentReportEntity.class))).thenAnswer(i -> {
            ContentReportEntity e = i.getArgument(0);
            if (e.getReportId() == null) { e.setReportId(UUID.randomUUID()); }
            return e;
        });
        when(careLinkage.route(any(CareLinkageEntity.class))).thenAnswer(i -> {
            CareLinkageEntity e = i.getArgument(0);
            e.setLinkageId(UUID.randomUUID());
            return e;
        });

        svc(careLinkage, reports).report(TENANT, "reporter", "COMMENT", SUBJECT, "victim", "ABUSE", null);

        ArgumentCaptor<CareLinkageEntity> captor = ArgumentCaptor.forClass(CareLinkageEntity.class);
        verify(careLinkage).route(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo("URGENT");
    }

    @Test
    void spamReport_doesNotEscalateToCare() {
        CareLinkageService careLinkage = mock(CareLinkageService.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        when(reports.save(any(ContentReportEntity.class))).thenAnswer(i -> {
            ContentReportEntity e = i.getArgument(0);
            if (e.getReportId() == null) { e.setReportId(UUID.randomUUID()); }
            return e;
        });

        ContentReportEntity r = svc(careLinkage, reports)
                .report(TENANT, "reporter", "POST", SUBJECT, "owner", "SPAM", null);

        verify(careLinkage, never()).route(any());
        assertThat(r.getCareLinkageId()).isNull();
        assertThat(r.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void safetyReasonHelper() {
        assertThat(SocialModerationService.isSafetyReason("SELF_HARM")).isTrue();
        assertThat(SocialModerationService.isSafetyReason("ABUSE")).isTrue();
        assertThat(SocialModerationService.isSafetyReason("SPAM")).isFalse();
        assertThat(SocialModerationService.isSafetyReason(null)).isFalse();
    }
}
