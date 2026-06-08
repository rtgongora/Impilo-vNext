package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventCertificateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventCertificateRepository extends JpaRepository<LiveEventCertificateEntity, UUID> {

    List<LiveEventCertificateEntity> findByEventId(UUID eventId);

    List<LiveEventCertificateEntity> findByParticipantId(String participantId);

    Optional<LiveEventCertificateEntity> findByEventIdAndParticipantIdAndCertificateType(
            UUID eventId, String participantId, String certificateType);

    Optional<LiveEventCertificateEntity> findByVerificationCode(String verificationCode);
}
