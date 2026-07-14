package zw.gov.mohcc.impilo.vashandi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vashandi.core.TheatreTeamService;
import zw.gov.mohcc.impilo.vashandi.core.VashandiOutboxWriter;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TheatreTeamMemberEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TheatreTeamMemberRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TheatreTeamRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TheatreTeamServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TheatreTeamRepository teamRepository;
    private TheatreTeamMemberRepository memberRepository;
    private ShiftRepository shiftRepository;
    private VashandiOutboxWriter outbox;
    private TheatreTeamService service;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TheatreTeamRepository.class);
        memberRepository = mock(TheatreTeamMemberRepository.class);
        shiftRepository = mock(ShiftRepository.class);
        outbox = mock(VashandiOutboxWriter.class);
        service = new TheatreTeamService(teamRepository, memberRepository, shiftRepository, outbox);
        TrustContextHolder.set(new TrustContext(TENANT, "hr-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(teamRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private TheatreTeamEntity team() {
        TheatreTeamEntity t = new TheatreTeamEntity();
        t.setId(UUID.randomUUID());
        t.setTenantId(TENANT);
        t.setSessionRef(UUID.randomUUID());
        when(teamRepository.findByTenantIdAndId(TENANT, t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void memberWithoutShiftIsFlaggedButAccepted() {
        TheatreTeamEntity t = team();
        when(shiftRepository.countByTenantIdAndWorkforceProfileIdAndStatusIn(eq(TENANT), any(), anyCollection()))
                .thenReturn(0L);

        Map<String, Object> member = service.addMember(t.getId().toString(),
                Map.of("role", "surgeon", "workforceProfileId", UUID.randomUUID().toString()));

        assertThat(member.get("shiftCoverage")).isEqualTo(TheatreTeamMemberEntity.COVERAGE_NO_SHIFT);
        assertThat(member.get("role")).isEqualTo("SURGEON");
    }

    @Test
    void memberWithShiftIsCovered() {
        TheatreTeamEntity t = team();
        when(shiftRepository.countByTenantIdAndWorkforceProfileIdAndStatusIn(eq(TENANT), any(), anyCollection()))
                .thenReturn(2L);

        Map<String, Object> member = service.addMember(t.getId().toString(),
                Map.of("role", "anaesthetist", "workforceProfileId", UUID.randomUUID().toString()));

        assertThat(member.get("shiftCoverage")).isEqualTo(TheatreTeamMemberEntity.COVERAGE_COVERED);
    }

    @Test
    void confirmRequiresMembers() {
        TheatreTeamEntity t = team();
        when(memberRepository.findByTeamId(t.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirmTeam(t.getId().toString()))
                .hasMessageContaining("no members");
    }

    @Test
    void confirmMovesTeamToConfirmed() {
        TheatreTeamEntity t = team();
        TheatreTeamMemberEntity m = new TheatreTeamMemberEntity();
        m.setId(UUID.randomUUID());
        m.setRole("SURGEON");
        m.setWorkforceProfileId(UUID.randomUUID());
        when(memberRepository.findByTeamId(t.getId())).thenReturn(List.of(m));

        Map<String, Object> result = service.confirmTeam(t.getId().toString());

        assertThat(result.get("status")).isEqualTo("CONFIRMED");
        assertThat(m.isConfirmed()).isTrue();
    }
}
