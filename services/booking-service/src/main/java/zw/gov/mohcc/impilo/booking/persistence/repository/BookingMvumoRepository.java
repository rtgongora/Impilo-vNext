package zw.gov.mohcc.impilo.booking.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.booking.domain.MvumoType;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingMvumoEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingMvumoRepository extends JpaRepository<BookingMvumoEntity, UUID> {

    List<BookingMvumoEntity> findByBookingIdAndTenantId(UUID bookingId, UUID tenantId);

    Optional<BookingMvumoEntity> findByBookingIdAndMvumoType(UUID bookingId, MvumoType mvumoType);
}
