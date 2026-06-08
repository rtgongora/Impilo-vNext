package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.booking.domain.BookingStatus;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingAuditEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingAuditRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;
import java.util.UUID;

@Service
public class BookingAuditService {

    private final BookingAuditRepository auditRepository;

    public BookingAuditService(BookingAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void record(TrustContext ctx, UUID bookingId, String action,
                       BookingStatus from, BookingStatus to, Map<String, Object> detail) {
        BookingAuditEntity audit = new BookingAuditEntity();
        audit.setTenantId(ctx.tenantId());
        audit.setBookingId(bookingId);
        audit.setAction(action);
        audit.setActorId(ctx.actorId());
        audit.setFromStatus(from != null ? from.name() : null);
        audit.setToStatus(to != null ? to.name() : null);
        audit.setDetail(detail);
        auditRepository.save(audit);
    }
}
