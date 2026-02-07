package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentDirectiveRepository extends JpaRepository<ConsentDirectiveEntity, UUID> {

    /**
     * Paginated lookup of consent directives for a given patient in a tenant, filtered by status.
     * Used for patient consent listing and portal views.
     */
    Page<ConsentDirectiveEntity> findByTenantIdAndPatientRefAndStatus(
            UUID tenantId,
            String patientRef,
            String status,
            Pageable pageable
    );

    /**
     * Evaluation query: find all matching consent directives for a specific
     * (tenant, patient, grantee, purpose, status, provision) combination.
     * This is the hot-path query called during consent evaluation.
     */
    List<ConsentDirectiveEntity> findByTenantIdAndPatientRefAndGranteeRefAndPurposeAndStatusAndProvision(
            UUID tenantId,
            String patientRef,
            String granteeRef,
            String purpose,
            String status,
            String provision
    );

    /**
     * Quick existence check for a specific consent directive matching all criteria.
     * Used for deduplication before creating new consent directives.
     */
    boolean existsByTenantIdAndPatientRefAndGranteeRefAndPurposeAndScopeAndStatus(
            UUID tenantId,
            String patientRef,
            String granteeRef,
            String purpose,
            String scope,
            String status
    );

    /**
     * Find all active consent directives for a patient in a tenant, regardless of grantee.
     * Used during evaluation when we need to check ALL directives for a patient.
     */
    @Query("SELECT c FROM ConsentDirectiveEntity c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.patientRef = :patientRef " +
            "AND c.status = 'ACTIVE'")
    List<ConsentDirectiveEntity> findAllActiveForPatient(
            @Param("tenantId") UUID tenantId,
            @Param("patientRef") String patientRef
    );

    /**
     * Find all active consent directives matching tenant, patient, grantee, and purpose.
     * Returns both permit and deny provisions for evaluation logic to process.
     */
    @Query("SELECT c FROM ConsentDirectiveEntity c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.patientRef = :patientRef " +
            "AND (c.granteeRef = :granteeRef OR c.granteeRef IS NULL) " +
            "AND c.purpose = :purpose " +
            "AND c.status = 'ACTIVE'")
    List<ConsentDirectiveEntity> findActiveForEvaluation(
            @Param("tenantId") UUID tenantId,
            @Param("patientRef") String patientRef,
            @Param("granteeRef") String granteeRef,
            @Param("purpose") String purpose
    );

    /**
     * Count active consents for a patient (used for portal summary).
     */
    long countByTenantIdAndPatientRefAndStatus(UUID tenantId, String patientRef, String status);
}
