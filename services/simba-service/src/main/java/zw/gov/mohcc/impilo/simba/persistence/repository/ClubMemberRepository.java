package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubMemberEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMemberEntity, Long> {

    Optional<ClubMemberEntity> findByClubIdAndPersonCpid(UUID clubId, String personCpid);

    long deleteByClubIdAndPersonCpid(UUID clubId, String personCpid);

    long countByClubId(UUID clubId);
}
