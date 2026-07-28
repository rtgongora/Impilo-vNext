package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FCV-W5 — a claimant prepares privately; a steward decides whether it becomes truth.
 *
 * <p>The properties that matter: a draft never describes the facility, a facility cannot assert
 * things that are decided <em>about</em> it, and accepting a profile is not verifying a facility.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityProfileSubmissionServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private FacilityRepository facilityRepository;

    private FacilityProfileSubmissionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityProfileSubmissionService(jdbc, facilityRepository);
        TrustContextHolder.set(new TrustContext(tenantId, "HID-CLAIMANT", "OPERATOR", "ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        FacilityEntity f = new FacilityEntity();
        f.setId(1L);
        f.setFacilityUuid(facilityUuid);
        f.setTenantId(tenantId);
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(f));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubOpenSubmission(String state) {
        when(jdbc.queryForList(anyString(), any(UUID.class), anyString()))
                .thenReturn(List.of(Map.of("id", submissionId)));
        when(jdbc.queryForList(anyString(), any(UUID.class)))
                .thenReturn(List.of(Map.of("state", state, "correction_note", "")));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any(),
                any(Object[].class))).thenReturn(1);
    }

    // ── what a facility may and may not say about itself ────────────────────────────────────

    @Test
    void aFacilityCannotAssertThingsThatAreDecidedAboutIt() {
        // Status, regulatory standing and legitimacy are judgements made about a facility. Letting
        // it propose them would be self-legitimisation through the back door of a profile edit.
        for (String forbidden : List.of("status", "regulatory_status", "operational_status",
                "allowed_on_platform", "verification_state")) {
            assertThatThrownBy(() -> service.proposeField(facilityUuid, forbidden, "ACTIVE"))
                    .as("proposing %s must be refused", forbidden)
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO tuso.facility_field_assertion"),
                any(Object[].class));
    }

    @Test
    void aProposedValueIsWrittenAsAPrivateNotCurrentAssertion() {
        stubOpenSubmission("DRAFT");

        service.proposeField(facilityUuid, "address_line1", "12 Samora Machel Ave");

        // FACILITY_CLAIM was in the assertion vocabulary from the start with no writer; a facility's
        // own statement now carries the same provenance as the regulator's — and is_current=false
        // means it does not describe the facility yet.
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("'FACILITY_CLAIM'"),
                any(), any(), any(), any(), any());
    }

    // ── the correction loop ─────────────────────────────────────────────────────────────────

    @Test
    void aCorrectionRequestMustSayWhatToFix() {
        when(jdbc.queryForList(anyString(), any(UUID.class)))
                .thenReturn(List.of(Map.of("state", "SUBMITTED", "correction_note", "")));

        assertThatThrownBy(() -> service.requestCorrection(submissionId, "  "))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void answeringACorrectionIsARESUBMISSION_soAReviewerSeesItIsASecondLook() {
        stubOpenSubmission("CORRECTION_REQUIRED");

        service.submit(facilityUuid);

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET state=?, submitted_at=now()"),
                org.mockito.ArgumentMatchers.eq("RESUBMITTED"), any());
    }

    @Test
    void aSubmissionWithAReviewerCannotBeEditedByTheClaimant() {
        stubOpenSubmission("SUBMITTED");

        assertThatThrownBy(() -> service.proposeField(facilityUuid, "phone", "+263..."))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void anEmptySubmissionIsRefused() {
        when(jdbc.queryForList(anyString(), any(UUID.class), anyString()))
                .thenReturn(List.of(Map.of("id", submissionId)));
        when(jdbc.queryForList(anyString(), any(UUID.class)))
                .thenReturn(List.of(Map.of("state", "DRAFT", "correction_note", "")));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any(),
                any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> service.submit(facilityUuid))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onlyASubmittedProfileCanBeDecided() {
        when(jdbc.queryForList(anyString(), any(UUID.class)))
                .thenReturn(List.of(Map.of("state", "DRAFT", "correction_note", "")));

        assertThatThrownBy(() -> service.accept(submissionId, "ok"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reject(submissionId, "no"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptingPromotesTheProposalsAndSupersedesWhatTheyReplace() {
        stubOpenSubmission("SUBMITTED");

        service.accept(submissionId, "Address confirmed against the signed letter");

        // Previous current assertion is superseded (kept as history), then the proposals become
        // current — a verified field is never silently overwritten.
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET is_current=false"), any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("SET is_current=true, promoted_at=now()"),
                any(Object[].class));
    }
}
