package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralPackageEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralPackageRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralPackageServiceTest {
    @Mock
    private ReferralPackageRepository referralRepository;
    @Mock
    private EncounterRepository encounterRepository;

    private ReferralPackageService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new ReferralPackageService(referralRepository, encounterRepository, new ObjectMapper());
        tenantId = UUID.randomUUID();
    }

    @Test
    void submitBlocksWhenConsentRequiredAndNotObtained() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralPackageEntity referral = new ReferralPackageEntity();
            referral.setReferralId(referralId);
            referral.setTenantId(tenantId);
            referral.setReferralPackageStatus("DRAFT");
            referral.setReferralLetter("letter");
            referral.setRoutingTarget("{\"type\":\"PRACTITIONER\",\"target_ref\":\"PROV-1\"}");
            referral.setVirtualMode("video");
            referral.setConsentRequired(true);
            referral.setConsentStatus("PENDING");
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId)).thenReturn(Optional.of(referral));

            assertThatThrownBy(() -> service.submit(referralId))
                    .isInstanceOf(PctDomainException.class)
                    .hasMessageContaining("Consent is required");
        }
    }

    @Test
    void createDraftDefaultsToVirtualModality() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            EncounterEntity encounter = new EncounterEntity();
            encounter.setId(44L);
            encounter.setJourneyId("J-1");
            encounter.setSubjectCpid("CPID-1");
            when(encounterRepository.findByTenantIdAndId(tenantId, 44L)).thenReturn(Optional.of(encounter));
            when(referralRepository.save(any(ReferralPackageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ReferralPackageEntity created = service.createDraft(Map.of("encounter_id", "44", "reason", "Need review"));
            assertThat(created.getModality()).isEqualTo("virtual");
            assertThat(created.getVirtualMode()).isEqualTo("video");
            assertThat(created.getConsentStatus()).isEqualTo("NOT_REQUIRED");
        }
    }

    @Test
    void createDraftRejectsNonUuidAttachmentReferences() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            EncounterEntity encounter = new EncounterEntity();
            encounter.setId(44L);
            encounter.setJourneyId("J-1");
            encounter.setSubjectCpid("CPID-1");
            when(encounterRepository.findByTenantIdAndId(tenantId, 44L)).thenReturn(Optional.of(encounter));

            assertThatThrownBy(() -> service.createDraft(Map.of(
                    "encounter_id", "44",
                    "attachment_document_ids", java.util.List.of("bad-id"))))
                    .isInstanceOf(PctDomainException.class)
                    .hasMessageContaining("UUID references only");
        }
    }

    @Test
    void submitRejectsRoutingTargetWithoutTypeOrReference() {
        try (MockedStatic<TrustContextHolder> mocked = org.mockito.Mockito.mockStatic(TrustContextHolder.class)) {
            mocked.when(TrustContextHolder::require).thenReturn(context());
            UUID referralId = UUID.randomUUID();
            ReferralPackageEntity referral = new ReferralPackageEntity();
            referral.setReferralId(referralId);
            referral.setTenantId(tenantId);
            referral.setReferralPackageStatus("DRAFT");
            referral.setReferralLetter("letter");
            referral.setRoutingTarget("{\"target_ref\":\"abc\"}");
            referral.setVirtualMode("video");
            referral.setConsentRequired(false);
            when(referralRepository.findByTenantIdAndReferralId(tenantId, referralId)).thenReturn(Optional.of(referral));

            assertThatThrownBy(() -> service.submit(referralId))
                    .isInstanceOf(PctDomainException.class)
                    .hasMessageContaining("routing_target.type is required");
        }
    }

    private TrustContext context() {
        return new TrustContext(
                tenantId, "actor-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }
}
