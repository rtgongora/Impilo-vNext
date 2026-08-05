package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceResponsibilityProfileEntity;

import java.util.List;
import java.util.UUID;

public interface ServiceResponsibilityProfileRepository
        extends JpaRepository<ServiceResponsibilityProfileEntity, UUID> {

    Page<ServiceResponsibilityProfileEntity> findByStatus(String status, Pageable pageable);

    List<ServiceResponsibilityProfileEntity> findByProfileCodeOrderByVersionDesc(String profileCode);

    List<ServiceResponsibilityProfileEntity> findBySupersedesProfileId(UUID supersedesProfileId);
}
