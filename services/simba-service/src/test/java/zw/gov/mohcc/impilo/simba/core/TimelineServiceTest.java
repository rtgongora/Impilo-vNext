package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.simba.persistence.entity.TimelineEventEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.TimelineEventRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pure unit test of TimelineService keyset pagination and next-cursor computation. */
class TimelineServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CPID = "unit-timeline-001";

    private TimelineEventEntity row(long id) {
        TimelineEventEntity e = new TimelineEventEntity();
        try {
            Field f = TimelineEventEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        e.setTenantId(TENANT);
        e.setPersonCpid(CPID);
        e.setEventCategory("ASSESSMENT");
        e.setTitle("Row " + id);
        return e;
    }

    @Test
    void firstPage_full_returnsNextCursor() {
        TimelineEventRepository repo = mock(TimelineEventRepository.class);
        // limit 2 -> service requests 3 (limit+1); repo returns 3 -> a further page exists.
        List<TimelineEventEntity> rows = new ArrayList<>(List.of(row(10), row(9), row(8)));
        when(repo.firstPage(eq(TENANT), eq(CPID), any(Pageable.class))).thenReturn(rows);

        TimelineService svc = new TimelineService(repo, new ObjectMapper());
        TimelineService.TimelinePage page = svc.page(TENANT, CPID, null, 2);

        assertThat(page.events()).hasSize(2);
        assertThat(page.events().get(0).getId()).isEqualTo(10L);
        assertThat(page.nextCursor()).isEqualTo(9L); // id of the last returned row
    }

    @Test
    void lastPage_partial_returnsNullCursor() {
        TimelineEventRepository repo = mock(TimelineEventRepository.class);
        // Only 1 row available for a limit-of-2 request (service asked for 3) -> no further page.
        when(repo.afterCursor(eq(TENANT), eq(CPID), eq(9L), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(row(1))));

        TimelineService svc = new TimelineService(repo, new ObjectMapper());
        TimelineService.TimelinePage page = svc.page(TENANT, CPID, 9L, 2);

        assertThat(page.events()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void limitIsCappedAndFloored() {
        TimelineEventRepository repo = mock(TimelineEventRepository.class);
        when(repo.firstPage(eq(TENANT), eq(CPID), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        TimelineService svc = new TimelineService(repo, new ObjectMapper());

        // limit 0 -> floored to 1; still no rows -> empty, null cursor
        assertThat(svc.page(TENANT, CPID, null, 0).nextCursor()).isNull();
        assertThat(svc.page(TENANT, CPID, null, 9999).events()).isEmpty();
    }

    @Test
    void record_persistsWithDetailJson() {
        TimelineEventRepository repo = mock(TimelineEventRepository.class);
        when(repo.save(any(TimelineEventEntity.class))).thenAnswer(i -> i.getArgument(0));
        TimelineService svc = new TimelineService(repo, new ObjectMapper());

        TimelineEventEntity saved = svc.record(TENANT, CPID, "RISK_CHANGE", "Risk updated",
                "Band moved to HIGH", "ATTENTION", "ASSESSMENT", "abc",
                Map.of("band", "HIGH"));

        assertThat(saved.getEventCategory()).isEqualTo("RISK_CHANGE");
        assertThat(saved.getSeverity()).isEqualTo("ATTENTION");
        assertThat(saved.getDetail()).contains("HIGH");
    }
}
