package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceInvitationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceInvitationRepository extends JpaRepository<MarketplaceInvitationEntity, String> {

    List<MarketplaceInvitationEntity> findByRequestId(String requestId);

    Optional<MarketplaceInvitationEntity> findFirstByRequestIdAndVendorId(String requestId, String vendorId);
}
