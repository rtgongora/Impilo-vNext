package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubMemberEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ClubMemberRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.ClubRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;

    public ClubService(ClubRepository clubRepository, ClubMemberRepository clubMemberRepository) {
        this.clubRepository = clubRepository;
        this.clubMemberRepository = clubMemberRepository;
    }

    @Transactional
    public ClubEntity create(ClubEntity club) {
        return clubRepository.save(club);
    }

    @Transactional(readOnly = true)
    public List<ClubEntity> list(UUID tenantId) {
        return clubRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public ClubMemberEntity join(UUID clubId, UUID tenantId, String personCpid, String role) {
        ClubEntity club = clubRepository.findByClubIdAndTenantId(clubId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        if (clubMemberRepository.findByClubIdAndPersonCpid(clubId, personCpid).isPresent()) {
            throw new IllegalStateException("Already a member");
        }
        if (club.getMaxMembers() != null
                && clubMemberRepository.countByClubId(clubId) >= club.getMaxMembers()) {
            throw new IllegalStateException("Club is full");
        }
        ClubMemberEntity member = new ClubMemberEntity();
        member.setTenantId(tenantId);
        member.setClubId(club.getClubId());
        member.setPersonCpid(personCpid);
        if (role != null && !role.isBlank()) {
            member.setRole(role);
        }
        return clubMemberRepository.save(member);
    }

    @Transactional
    public void leave(UUID clubId, String personCpid) {
        long removed = clubMemberRepository.deleteByClubIdAndPersonCpid(clubId, personCpid);
        if (removed == 0) {
            throw new IllegalArgumentException("Membership not found");
        }
    }
}
