package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConfidentialityCapacityEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConfidentialityCapacityRepository extends JpaRepository<ConfidentialityCapacityEntity, UUID> {

    /**
     * Determinations for this person and care domain made on or before the act date, newest first.
     * Bounded by the act date deliberately: a determination made AFTER a record was written cannot
     * retrospectively explain how that record was stamped.
     */
    @Query("SELECT d FROM ConfidentialityCapacityEntity d "
            + "WHERE d.tenantId = :tenantId AND d.subjectCpid = :cpid AND d.category = :category "
            + "AND d.determinedOn <= :asOf "
            + "ORDER BY d.determinedOn DESC")
    List<ConfidentialityCapacityEntity> findAsOf(@Param("tenantId") UUID tenantId,
                                                 @Param("cpid") String cpid,
                                                 @Param("category") String category,
                                                 @Param("asOf") LocalDate asOf);
}
