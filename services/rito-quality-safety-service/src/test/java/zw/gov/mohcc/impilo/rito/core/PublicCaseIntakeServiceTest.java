package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.CaseRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PublicCaseIntakeServiceTest {

    @Autowired private PublicCaseIntakeService intakeService;
    @Autowired private CaseRepository caseRepository;

    @Test
    void public_case_issues_claim_code_and_stores_only_its_hash() {
        UUID tenant = UUID.randomUUID();
        var result = intakeService.openPublicCase(tenant, "SAFETY_CONCERN",
                "Unsafe ward", "Broken equipment left in the corridor", null, null);

        assertThat(result.claimCode()).hasSize(20);
        assertThat(result.caseReference()).startsWith("RITO-C-");

        CaseEntity stored = caseRepository.findById(result.caseId()).orElseThrow();
        assertThat(stored.getClaimCodeHash())
                .isEqualTo(PublicCaseIntakeService.sha256Hex(result.claimCode()))
                .isNotEqualTo(result.claimCode());
        // Anonymity is forced on the public lane: no reporter identity, ever.
        assertThat(stored.getAnonymous()).isTrue();
        assertThat(stored.getReporterActorId()).isNull();
    }

    @Test
    void status_is_readable_only_with_the_exact_claim_code() {
        UUID tenant = UUID.randomUUID();
        var result = intakeService.openPublicCase(tenant, "COMPLAINT",
                "Long wait", "Waited five hours without triage", null, null);

        var status = intakeService.statusByClaimCode(tenant, result.claimCode());
        assertThat(status).isPresent();
        assertThat(status.get().caseReference()).isEqualTo(result.caseReference());
        assertThat(status.get().status()).isEqualTo("SUBMITTED");

        // Wrong code, case reference, or another tenant's lookup all miss.
        assertThat(intakeService.statusByClaimCode(tenant, "WRONGCODEWRONGCODE22")).isEmpty();
        assertThat(intakeService.statusByClaimCode(tenant, result.caseReference())).isEmpty();
        assertThat(intakeService.statusByClaimCode(UUID.randomUUID(), result.claimCode())).isEmpty();
    }

    @Test
    void volunteered_contact_lands_in_metadata_not_identity() {
        UUID tenant = UUID.randomUUID();
        var result = intakeService.openPublicCase(tenant, "COMPLAINT",
                "Rude reception", "Details of the encounter", null, "+263771234567");

        CaseEntity stored = caseRepository.findById(result.caseId()).orElseThrow();
        assertThat(stored.getAnonymous()).isTrue();
        assertThat(stored.getReporterActorId()).isNull();
        assertThat(stored.getMetadataJson()).contains("+263771234567").contains("publicLane");
    }

    @Test
    void public_lane_rejects_non_allowlisted_case_types() {
        UUID tenant = UUID.randomUUID();
        for (String rejected : new String[]{"COMPLIMENT", "SUGGESTION", "ADVERSE_EVENT", null}) {
            assertThatThrownBy(() -> intakeService.openPublicCase(tenant, rejected,
                    "t", "d", null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
