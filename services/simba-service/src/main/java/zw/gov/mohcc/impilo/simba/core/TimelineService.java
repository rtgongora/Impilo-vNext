package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.TimelineEventEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.TimelineEventRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Person-facing wellness timeline. {@link #record} is called synchronously inside each business
 * transaction (assessment, risk change, plan enrolment, reminder, follow-up, care linkage) so the
 * timeline the person sees is transactional and consistent with the write that produced it. This is
 * deliberately NOT projected from the Kafka outbox: the outbox has no in-service projector, and the
 * timeline is user-facing rather than integration-facing.
 */
@Service
public class TimelineService {

    private final TimelineEventRepository repository;
    private final ObjectMapper objectMapper;

    public TimelineService(TimelineEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Records a timeline event. Detail is optional structured context (never clinical raw values). */
    @Transactional
    public TimelineEventEntity record(UUID tenantId,
                                      String personCpid,
                                      String category,
                                      String title,
                                      String summary,
                                      String severity,
                                      String refType,
                                      String refId,
                                      Map<String, Object> detail) {
        TimelineEventEntity e = new TimelineEventEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(personCpid);
        e.setEventCategory(category);
        e.setTitle(title);
        e.setSummary(summary);
        e.setSeverity(severity == null ? "INFO" : severity);
        e.setRefType(refType);
        e.setRefId(refId);
        if (detail != null && !detail.isEmpty()) {
            try {
                e.setDetail(objectMapper.writeValueAsString(detail));
            } catch (JsonProcessingException ex) {
                // Detail is best-effort context; never fail the business txn over it.
                e.setDetail(null);
            }
        }
        return repository.save(e);
    }

    /** A page of timeline events plus the keyset cursor for the next page (null when exhausted). */
    public record TimelinePage(List<TimelineEventEntity> events, Long nextCursor) { }

    /**
     * Keyset pagination by descending id. {@code cursor} is the last id from the previous page;
     * null/blank means the first page.
     */
    @Transactional(readOnly = true)
    public TimelinePage page(UUID tenantId, String personCpid, Long cursor, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        // Fetch one extra to know whether a further page exists.
        PageRequest req = PageRequest.of(0, capped + 1);
        List<TimelineEventEntity> rows = (cursor == null)
                ? repository.firstPage(tenantId, personCpid, req)
                : repository.afterCursor(tenantId, personCpid, cursor, req);

        Long nextCursor = null;
        if (rows.size() > capped) {
            rows = rows.subList(0, capped);
            nextCursor = rows.get(rows.size() - 1).getId();
        }
        return new TimelinePage(rows, nextCursor);
    }
}
