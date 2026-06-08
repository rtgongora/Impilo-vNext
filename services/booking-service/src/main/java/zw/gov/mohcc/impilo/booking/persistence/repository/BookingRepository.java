package zw.gov.mohcc.impilo.booking.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.booking.domain.BookingStatus;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;

import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    Optional<BookingEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<BookingEntity> findByBookingNumberAndTenantId(String bookingNumber, UUID tenantId);

    @Query("""
            SELECT b FROM BookingEntity b
            WHERE b.tenantId = :tenantId
              AND (:clientId IS NULL OR b.clientId = :clientId)
              AND (:providerId IS NULL OR b.providerId = :providerId)
              AND (:facilityId IS NULL OR b.facilityId = :facilityId)
              AND (:status IS NULL OR b.bookingStatus = :status)
            ORDER BY b.createdAt DESC
            """)
    Page<BookingEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("clientId") String clientId,
            @Param("providerId") String providerId,
            @Param("facilityId") Long facilityId,
            @Param("status") BookingStatus status,
            Pageable pageable);
}
