package zw.gov.mohcc.impilo.booking.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingAuditEntity;

import java.util.List;
import java.util.UUID;

public interface BookingAuditRepository extends JpaRepository<BookingAuditEntity, Long> {

    List<BookingAuditEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}
