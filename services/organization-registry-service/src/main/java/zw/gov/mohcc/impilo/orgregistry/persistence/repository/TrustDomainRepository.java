package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.TrustDomainEntity;

import java.util.Optional;
import java.util.UUID;

public interface TrustDomainRepository extends JpaRepository<TrustDomainEntity, UUID> {

    Optional<TrustDomainEntity> findByCode(String code);

    boolean existsByCode(String code);

    Page<TrustDomainEntity> findByAccreditationStatus(String accreditationStatus, Pageable pageable);

    @Query("select t from TrustDomainEntity t where "
            + "(:status is null or t.accreditationStatus = :status) and "
            + "(:controllerType is null or t.controllerType = :controllerType)")
    Page<TrustDomainEntity> search(@Param("status") String status,
                                   @Param("controllerType") String controllerType,
                                   Pageable pageable);
}
