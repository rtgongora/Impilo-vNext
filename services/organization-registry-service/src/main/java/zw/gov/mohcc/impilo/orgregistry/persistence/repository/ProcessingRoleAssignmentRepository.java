package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ProcessingRoleAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ProcessingRoleAssignmentRepository
        extends JpaRepository<ProcessingRoleAssignmentEntity, UUID> {

    /**
     * Active assignments matching an exact (scope, data domain, purpose, role) at an instant.
     * This is the innermost step of the §3A.4 resolution order — most specific scope first.
     */
    @Query("select a from ProcessingRoleAssignmentEntity a where a.scopeType = :scopeType "
            + "and a.scopeId = :scopeId and a.dataDomain = :dataDomain and a.purpose = :purpose "
            + "and a.roleType = :roleType and a.status = 'ACTIVE' "
            + "and a.effectiveFrom <= :at and (a.effectiveTo is null or a.effectiveTo > :at)")
    List<ProcessingRoleAssignmentEntity> findActiveFor(@Param("scopeType") String scopeType,
                                                       @Param("scopeId") UUID scopeId,
                                                       @Param("dataDomain") String dataDomain,
                                                       @Param("purpose") String purpose,
                                                       @Param("roleType") String roleType,
                                                       @Param("at") OffsetDateTime at);

    List<ProcessingRoleAssignmentEntity> findByScopeTypeAndScopeIdOrderByRecordedAtDesc(
            String scopeType, UUID scopeId);
}
