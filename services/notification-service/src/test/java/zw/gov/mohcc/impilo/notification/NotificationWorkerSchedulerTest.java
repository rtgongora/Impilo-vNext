package zw.gov.mohcc.impilo.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.notification.api.dto.WorkerResponse;
import zw.gov.mohcc.impilo.notification.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.notification.service.NotificationWorkerScheduler;
import zw.gov.mohcc.impilo.notification.service.WorkerService;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        // application-test.yml disables the loop so it cannot race other tests;
        // re-enable here to prove the bean registers when the default applies.
        properties = {"notification.worker.scheduler-enabled=true",
                "notification.worker.poll-interval-ms=3600000"})
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class NotificationWorkerSchedulerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Scheduler bean registers when enabled so pending notifications dispatch without manual worker runs")
    void schedulerBeanRegisteredWhenEnabled() {
        assertThat(applicationContext.getBeansOfType(NotificationWorkerScheduler.class)).hasSize(1);
    }

    @Test
    @DisplayName("dispatchPending carries @Scheduled with a configurable fixed delay")
    void dispatchPendingIsScheduled() throws Exception {
        Method method = NotificationWorkerScheduler.class.getMethod("dispatchPending");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${notification.worker.poll-interval-ms:10000}");
    }

    @Test
    @DisplayName("dispatchPending drains the pending queue with the configured batch limit")
    void dispatchPendingDelegatesToWorkerService() {
        WorkerService workerService = mock(WorkerService.class);
        when(workerService.processPending(7)).thenReturn(new WorkerResponse(3, 2, 1));

        NotificationWorkerScheduler scheduler = new NotificationWorkerScheduler(workerService, 7);
        scheduler.dispatchPending();

        verify(workerService).processPending(7);
    }

    @Test
    @DisplayName("dispatchPending swallows worker failures so the fixed-delay loop survives")
    void dispatchPendingSurvivesWorkerFailure() {
        WorkerService workerService = mock(WorkerService.class);
        doThrow(new RuntimeException("db down")).when(workerService).processPending(anyInt());

        NotificationWorkerScheduler scheduler = new NotificationWorkerScheduler(workerService, 25);
        scheduler.dispatchPending();

        verify(workerService).processPending(25);
    }
}
