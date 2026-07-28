package zw.gov.mohcc.impilo.madi.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.madi.persistence.entity.MhpPackEntity;

public interface MhpPackRepository extends JpaRepository<MhpPackEntity, UUID> {
    List<MhpPackEntity> findByActivationIdOrderByPackNumberAsc(UUID activationId);
}
