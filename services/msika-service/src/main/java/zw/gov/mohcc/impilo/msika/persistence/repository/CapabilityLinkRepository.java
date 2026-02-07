package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.CapabilityLinkEntity;

import java.util.List;

public interface CapabilityLinkRepository extends JpaRepository<CapabilityLinkEntity, String> {
    List<CapabilityLinkEntity> findByItemId(String itemId);
    List<CapabilityLinkEntity> findByCapabilityType(String capabilityType);
}
