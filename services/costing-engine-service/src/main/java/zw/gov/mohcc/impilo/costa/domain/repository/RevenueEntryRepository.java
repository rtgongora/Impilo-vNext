package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.RevenueEntryEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RevenueEntryRepository extends JpaRepository<RevenueEntryEntity, Long> {

    boolean existsByTenantIdAndBillId(UUID tenantId, String billId);

    Optional<RevenueEntryEntity> findFirstByTenantIdAndBillIdOrderByCreatedAtDesc(UUID tenantId, String billId);

    @Query("select coalesce(sum(r.netAmount), 0) from RevenueEntryEntity r where r.tenantId = :tenantId "
            + "and r.facilityId = :facilityId and r.periodYear = :year and r.periodMonth = :month")
    BigDecimal sumNetByFacilityAndPeriod(@Param("tenantId") UUID tenantId,
                                         @Param("facilityId") UUID facilityId,
                                         @Param("year") int year,
                                         @Param("month") int month);

    @Query("select coalesce(sum(r.grossAmount), 0) from RevenueEntryEntity r where r.tenantId = :tenantId "
            + "and r.facilityId = :facilityId and r.periodYear = :year and r.periodMonth = :month")
    BigDecimal sumGrossByFacilityAndPeriod(@Param("tenantId") UUID tenantId,
                                           @Param("facilityId") UUID facilityId,
                                           @Param("year") int year,
                                           @Param("month") int month);

    @Query("select coalesce(sum(r.discountAmount), 0) from RevenueEntryEntity r where r.tenantId = :tenantId "
            + "and r.facilityId = :facilityId and r.periodYear = :year and r.periodMonth = :month")
    BigDecimal sumDiscountByFacilityAndPeriod(@Param("tenantId") UUID tenantId,
                                              @Param("facilityId") UUID facilityId,
                                              @Param("year") int year,
                                              @Param("month") int month);

    @Query("select r from RevenueEntryEntity r where r.tenantId = :tenantId and r.facilityId = :facilityId "
            + "and r.periodYear = :year and r.periodMonth = :month "
            + "and (:departmentId is null or r.departmentId = :departmentId) order by r.recordedAt desc")
    List<RevenueEntryEntity> findByFacilityPeriodAndOptionalDepartment(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("departmentId") String departmentId);
}
