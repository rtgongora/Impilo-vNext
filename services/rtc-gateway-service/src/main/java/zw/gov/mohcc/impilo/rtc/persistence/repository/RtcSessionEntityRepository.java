package zw.gov.mohcc.impilo.rtc.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.rtc.persistence.entity.RtcSessionEntity;

import java.util.Optional;

public interface RtcSessionEntityRepository extends JpaRepository<RtcSessionEntity, String> {

    Optional<RtcSessionEntity> findFirstByRoomName(String roomName);
}
