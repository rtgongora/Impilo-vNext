package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamMemberEntity;

import java.util.List;
import java.util.UUID;

public interface TheatreTeamMemberRepository extends JpaRepository<TheatreTeamMemberEntity, UUID> {

    List<TheatreTeamMemberEntity> findByTeamId(UUID teamId);
}
