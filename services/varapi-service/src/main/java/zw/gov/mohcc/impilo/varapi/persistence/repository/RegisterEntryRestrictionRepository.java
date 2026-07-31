package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegisterEntryRestrictionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegisterEntryRestrictionRepository extends JpaRepository<RegisterEntryRestrictionEntity, Long> {
    List<RegisterEntryRestrictionEntity> findByTenantIdAndRegistrationRecordIdInAndStatus(
            UUID tenantId, List<Long> registrationRecordIds, String status);

    /**
     * Council-scoped restriction read (operator desk). Joins through registration records so a
     * council never sees another council's conditions.
     */
    @Query(value = """
            SELECT r.id, r.restriction_type, r.description, r.status, r.valid_from, r.valid_to,
                   r.imposed_by, r.decision_ref, r.registration_record_id,
                   rec.registration_number, rec.provider_id, reg.register_code, reg.name
            FROM varapi.register_entry_restrictions r
            JOIN varapi.provider_council_registration_records rec ON rec.id = r.registration_record_id
            LEFT JOIN varapi.professional_registers reg ON reg.id = rec.register_id
            WHERE r.tenant_id = :tenantId AND rec.council_id = :councilId
            ORDER BY r.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findCouncilRestrictions(@Param("tenantId") UUID tenantId,
                                           @Param("councilId") Long councilId);
}
