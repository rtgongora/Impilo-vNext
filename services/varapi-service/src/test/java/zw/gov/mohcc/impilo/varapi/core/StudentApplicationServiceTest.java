package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationContributorInviteEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationSectionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationContributorInviteRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationSectionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bounded contribution, and the returning of work.
 *
 * <p>V040 already refuses a bad INVITE at the database. What these tests cover is the half a schema
 * cannot reach: a bad READ, and a bad WRITE through a good invite. The training institution
 * confirming a student's enrolment must not be able to see that student's identity documents, and
 * must not be able to write them by passing a different section key.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentApplicationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long APPLICATION = 42L;

    @Mock private ApplicationSectionRepository sectionRepository;
    @Mock private ApplicationContributorInviteRepository inviteRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StudentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StudentApplicationService(sectionRepository, inviteRepository);
    }

    private ApplicationContributorInviteEntity invite(String status, String... scope) {
        ApplicationContributorInviteEntity invite = new ApplicationContributorInviteEntity();
        invite.setId(7L);
        invite.setTenantId(TENANT);
        invite.setApplicationId(APPLICATION);
        invite.setContributorKind("TRAINING_INSTITUTION");
        invite.setScopeSections(scope);
        invite.setStatus(status);
        return invite;
    }

    private ApplicationSectionEntity section(String key, String state) {
        ApplicationSectionEntity section = new ApplicationSectionEntity();
        section.setId(1L);
        section.setTenantId(TENANT);
        section.setApplicationId(APPLICATION);
        section.setSectionKey(key);
        section.setLabel(key);
        section.setState(state);
        return section;
    }

    // ── The bounded read ─────────────────────────────────────────────────────────────────────

    @Test
    void aContributorLoadsOnlyTheSectionsItsInviteNames() {
        // The sections outside the scope are never assembled — not fetched then filtered, which is
        // the version of this that leaks the moment someone forgets the filter.
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L))
                .thenReturn(Optional.of(invite("INVITED", "enrolment")));
        when(sectionRepository.findByTenantIdAndApplicationIdAndSectionKeyInOrderBySequenceNoAsc(
                eq(TENANT), eq(APPLICATION), anyList()))
                .thenReturn(List.of(section("enrolment", "NOT_STARTED")));

        service.sectionsForContributor(TENANT, 7L);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(sectionRepository).findByTenantIdAndApplicationIdAndSectionKeyInOrderBySequenceNoAsc(
                eq(TENANT), eq(APPLICATION), keys.capture());
        assertThat(keys.getValue()).containsExactly("enrolment");
        // The unbounded query must never be the one a contributor path reaches for.
        verify(sectionRepository, never())
                .findByTenantIdAndApplicationIdOrderBySequenceNoAsc(any(), any());
    }

    @Test
    void aContributorCannotWriteASectionOutsideItsScope() {
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L))
                .thenReturn(Optional.of(invite("INVITED", "enrolment")));

        assertThatThrownBy(() -> service.contributorCompletesSection(
                TENANT, 7L, "identity", objectMapper.createObjectNode()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not cover that part");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void theRefusalDoesNotDiscloseWhatElseTheApplicationContains() {
        // A contributor probing for the shape of someone's application should learn nothing from
        // being refused. Naming the sections that DO exist would answer the question they asked.
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L))
                .thenReturn(Optional.of(invite("INVITED", "enrolment")));

        assertThatThrownBy(() -> service.contributorCompletesSection(
                TENANT, 7L, "identity", objectMapper.createObjectNode()))
                .hasMessageNotContaining("enrolment")
                .hasMessageNotContaining("identity");
    }

    @Test
    void anExpiredInviteStopsWorkingOnREADEvenIfNoSweepHasRun() {
        ApplicationContributorInviteEntity expired = invite("INVITED", "enrolment");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.sectionsForContributor(TENANT, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void aCompletedInviteCannotBeReused() {
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L))
                .thenReturn(Optional.of(invite("COMPLETED", "enrolment")));

        assertThatThrownBy(() -> service.sectionsForContributor(TENANT, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void aContributorWithinScopeCompletesTheirPartAndTheInviteCloses() {
        when(inviteRepository.findByTenantIdAndId(TENANT, 7L))
                .thenReturn(Optional.of(invite("ACCEPTED", "enrolment")));
        when(sectionRepository.findByTenantIdAndApplicationIdAndSectionKey(
                TENANT, APPLICATION, "enrolment"))
                .thenReturn(Optional.of(section("enrolment", "NOT_STARTED")));
        when(sectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationSectionEntity result = service.contributorCompletesSection(
                TENANT, 7L, "enrolment", objectMapper.createObjectNode());

        assertThat(result.getState()).isEqualTo("COMPLETE");
        ArgumentCaptor<ApplicationContributorInviteEntity> closed =
                ArgumentCaptor.forClass(ApplicationContributorInviteEntity.class);
        verify(inviteRepository).save(closed.capture());
        assertThat(closed.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    // ── Returning work rather than rejecting people ──────────────────────────────────────────

    @Test
    void aSectionCannotBeReturnedWithoutSayingWhatToChange() {
        assertThatThrownBy(() -> service.returnSection(TENANT, APPLICATION, "documents", "  ", "reg"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Say what needs to change");

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void resubmittingReopensOnlyTheReturnedSection() {
        when(sectionRepository.findByTenantIdAndApplicationIdAndSectionKey(
                TENANT, APPLICATION, "documents"))
                .thenReturn(Optional.of(section("documents", "RETURNED")));
        when(sectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationSectionEntity result = service.resubmitSection(
                TENANT, APPLICATION, "documents", objectMapper.createObjectNode());

        assertThat(result.getState()).isEqualTo("COMPLETE");
    }

    @Test
    void aSectionThatWasNotReturnedCannotBeResubmitted() {
        // Otherwise "resubmit" becomes a way to overwrite a section a regulator already accepted.
        when(sectionRepository.findByTenantIdAndApplicationIdAndSectionKey(
                TENANT, APPLICATION, "identity"))
                .thenReturn(Optional.of(section("identity", "COMPLETE")));

        assertThatThrownBy(() -> service.resubmitSection(
                TENANT, APPLICATION, "identity", objectMapper.createObjectNode()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("nothing to resubmit");
    }

    @Test
    void correctingASectionKeepsTheRecordOfWhatWasAsked() {
        ApplicationSectionEntity returned = section("documents", "RETURNED");
        returned.setReturnedReason("The certified copy is unreadable.");
        when(sectionRepository.findByTenantIdAndApplicationIdAndSectionKey(
                TENANT, APPLICATION, "documents")).thenReturn(Optional.of(returned));
        when(sectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationSectionEntity result = service.resubmitSection(
                TENANT, APPLICATION, "documents", objectMapper.createObjectNode());

        assertThat(result.getReturnedReason()).isEqualTo("The certified copy is unreadable.");
    }
}
