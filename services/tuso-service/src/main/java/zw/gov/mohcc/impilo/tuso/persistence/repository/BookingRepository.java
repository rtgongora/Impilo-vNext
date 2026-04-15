package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BookingEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    @Query("SELECT b FROM BookingEntity b WHERE b.resource.id = :resourceId " +
           "AND b.status <> 'CANCELLED' " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<BookingEntity> findConflicting(
            @Param("resourceId") UUID resourceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    Page<BookingEntity> findByFacilityIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long facilityId, Instant from, Instant to, Pageable pageable);

    @Query("SELECT b FROM BookingEntity b WHERE b.resource.id = :resourceId AND b.status <> :excludedStatus ORDER BY b.startTime ASC")
    Page<BookingEntity> findByResourceIdAndStatusNotOrderByStartTimeAsc(
            @Param("resourceId") UUID resourceId, @Param("excludedStatus") String excludedStatus, Pageable pageable);

    @Query("SELECT b FROM BookingEntity b WHERE b.resource.id = :resourceId AND b.startTime < :endTime AND b.endTime > :startTime")
    List<BookingEntity> findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThan(
            @Param("resourceId") UUID resourceId, @Param("endTime") Instant endTime, @Param("startTime") Instant startTime);

    @Query("SELECT b FROM BookingEntity b WHERE b.resource.id = :resourceId AND b.startTime < :endTime AND b.endTime > :startTime AND b.status <> :excludedStatus")
    List<BookingEntity> findByResourceIdAndStartTimeLessThanAndEndTimeGreaterThanAndStatusNot(
            @Param("resourceId") UUID resourceId, @Param("endTime") Instant endTime, @Param("startTime") Instant startTime, @Param("excludedStatus") String excludedStatus);
}
