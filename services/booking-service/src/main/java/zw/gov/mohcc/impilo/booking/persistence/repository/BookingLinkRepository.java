package zw.gov.mohcc.impilo.booking.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.booking.domain.LinkType;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingLinkEntity;

import java.util.List;
import java.util.UUID;

public interface BookingLinkRepository extends JpaRepository<BookingLinkEntity, UUID> {

    List<BookingLinkEntity> findByBookingIdAndTenantId(UUID bookingId, UUID tenantId);

    List<BookingLinkEntity> findByBookingIdAndLinkType(UUID bookingId, LinkType linkType);
}
