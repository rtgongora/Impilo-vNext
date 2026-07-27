package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureDefinitionRepository extends JpaRepository<ProcedureDefinitionEntity, UUID> {

    /**
     * The current published version of a code. A unique partial index guarantees at most one,
     * because two current versions would mean two answers to "what does this procedure require".
     */
    Optional<ProcedureDefinitionEntity> findByTenantIdAndDefinitionCodeAndStatus(
            UUID tenantId, String definitionCode, String status);

    @Query("""
            SELECT d FROM ProcedureDefinitionEntity d
            WHERE d.tenantId = :tenantId
              AND d.status = 'PUBLISHED'
              AND (:specialty IS NULL OR d.owningSpecialty = :specialty)
              AND (:category  IS NULL OR d.category = :category)
              AND (:query     IS NULL OR LOWER(d.clinicalName) LIKE LOWER(CONCAT('%', :query, '%'))
                                      OR LOWER(d.definitionCode) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY d.clinicalName
            """)
    List<ProcedureDefinitionEntity> search(@Param("tenantId") UUID tenantId,
                                           @Param("specialty") String specialty,
                                           @Param("category") String category,
                                           @Param("query") String query);

    /**
     * How many published definitions exist at all, regardless of filter.
     *
     * <p>This is what lets a caller tell "your filter matched nothing" from "the catalogue is
     * empty", which are different facts with different responses — and a specialty menu that
     * renders nothing because the catalogue failed to load silently removes capability from a
     * clinician. Distinguishing empty from unavailable is a delivery requirement of this
     * programme, not a nicety.</p>
     */
    @Query("SELECT COUNT(d) FROM ProcedureDefinitionEntity d WHERE d.tenantId = :tenantId AND d.status = 'PUBLISHED'")
    long countPublished(@Param("tenantId") UUID tenantId);
}
