package zw.gov.mohcc.impilo.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.notification.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TM-B9/B1: proves the scheduled-send due query only selects notifications whose
 * not_before gate is unset (immediate) or already elapsed, and holds future-dated ones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ScheduledSendDueQueryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
    }

    private String persist(OffsetDateTime notBefore, String to) {
        NotificationEntity e = new NotificationEntity();
        e.setChannel("SMS");
        e.setToAddr(to);
        e.setTenantId(TENANT_ID);
        e.setPodId(POD_ID);
        e.setStatus(NotificationStatus.PENDING);
        e.setNotBefore(notBefore);
        return notificationRepository.save(e).getId();
    }

    @Test
    @DisplayName("Due query holds a future-dated notification and selects NULL/past-dated ones")
    void dueQueryGatesOnNotBefore() {
        OffsetDateTime now = OffsetDateTime.now();
        String immediateId = persist(null, "+263770000001");
        String pastId = persist(now.minusHours(1), "+263770000002");
        String futureId = persist(now.plusHours(1), "+263770000003");

        List<NotificationEntity> due =
                notificationRepository.findDue(NotificationStatus.PENDING, now, PageRequest.of(0, 50));

        List<String> dueIds = due.stream().map(NotificationEntity::getId).toList();
        assertThat(dueIds).contains(immediateId, pastId);
        assertThat(dueIds).doesNotContain(futureId);
    }

    @Test
    @DisplayName("Future-dated notification becomes due once its not_before elapses")
    void futureBecomesDueAfterGate() {
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusMinutes(30);
        String futureId = persist(scheduledAt, "+263770000004");

        List<NotificationEntity> beforeDue =
                notificationRepository.findDue(NotificationStatus.PENDING, OffsetDateTime.now(), PageRequest.of(0, 50));
        assertThat(beforeDue.stream().map(NotificationEntity::getId)).doesNotContain(futureId);

        List<NotificationEntity> afterDue = notificationRepository.findDue(
                NotificationStatus.PENDING, scheduledAt.plusSeconds(1), PageRequest.of(0, 50));
        assertThat(afterDue.stream().map(NotificationEntity::getId)).contains(futureId);
    }
}
