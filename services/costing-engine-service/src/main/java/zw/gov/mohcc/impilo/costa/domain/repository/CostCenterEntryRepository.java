package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntryEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CostCenterEntryRepository extends JpaRepository<CostCenterEntryEntity, Long> {

    List<CostCenterEntryEntity> findByCenterIdAndPeriodYearAndPeriodMonthOrderByRecordedAtDesc(
            UUID centerId, int periodYear, int periodMonth);

    @Query("select coalesce(sum(e.amount), 0) from CostCenterEntryEntity e where e.centerId = :centerId "
            + "and e.periodYear = :year and e.periodMonth = :month and e.entryType = :entryType")
    BigDecimal sumAmountByCenterPeriodAndType(@Param("centerId") UUID centerId,
                                              @Param("year") int year,
                                              @Param("month") int month,
                                              @Param("entryType") String entryType);

    @Query("select coalesce(sum(e.amount), 0) from CostCenterEntryEntity e where e.centerId = :centerId "
            + "and e.periodYear = :year and e.entryType = :entryType")
    BigDecimal sumAmountByCenterYearAndType(@Param("centerId") UUID centerId,
                                            @Param("year") int year,
                                            @Param("entryType") String entryType);

    boolean existsByCenterId(UUID centerId);
}
