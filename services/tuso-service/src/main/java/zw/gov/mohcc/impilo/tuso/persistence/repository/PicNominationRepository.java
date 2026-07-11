package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PicNominationRepository extends JpaRepository<PicNominationEntity, UUID> {
    List<PicNominationEntity> findByFacilityIdOrderByNominatedAtDesc(Long facilityId);
    List<PicNominationEntity> findByProviderPublicIdOrderByNominatedAtDesc(String providerPublicId);
    List<PicNominationEntity> findByStateOrderByNominatedAtDesc(String state);
    List<PicNominationEntity> findByProviderPublicIdAndStateIn(String providerPublicId, Collection<String> states);
}
