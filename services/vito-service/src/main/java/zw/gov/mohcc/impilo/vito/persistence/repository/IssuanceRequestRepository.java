package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.IssuanceState;
import zw.gov.mohcc.impilo.vito.persistence.entity.IssuanceRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssuanceRequestRepository extends JpaRepository<IssuanceRequestEntity, Long> {

    Page<IssuanceRequestEntity> findByTenantIdAndState(UUID tenantId, IssuanceState state, Pageable pageable);

    List<IssuanceRequestEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Optional<IssuanceRequestEntity> findByTenantIdAndHealthIdAndStateNotIn(UUID tenantId, UUID healthId, List<IssuanceState> terminalStates);

    Page<IssuanceRequestEntity> findByTenantIdAndAssignedToAndStateIn(UUID tenantId, String assignedTo, List<IssuanceState> states, Pageable pageable);
}
