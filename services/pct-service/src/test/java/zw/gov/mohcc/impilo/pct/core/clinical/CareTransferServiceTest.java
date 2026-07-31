package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.CareTransferEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ConsultationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transfer of care (brief.md §14 / §23.10).
 *
 * <p>The property under test: ownership moves only on acceptance with an accepting_ref. Requesting,
 * recommending (on a consultation), and declining never move the patient.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CareTransferServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000042");
    private static final String CPID = "cpid-transfer-1";
    private static final String JOURNEY = "88888888-8888-4888-8888-888888888882";

    @Autowired
    private CareTransferService transfers;

    @Autowired
    private ConsultationService consultations;

    @Autowired
    private ConsultationRepository consultationRepository;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "med-1", "PRACTITIONER", "EMERGENCY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    private Map<String, Object> transferBody(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subject_cpid", CPID);
        m.put("journey_id", JOURNEY);
        m.put("from_service", "MEDICINE");
        m.put("to_service", "SURGERY");
        m.put("reason", "Abdomen is surgical — take over");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    private ConsultationEntity answeredConsultationWithTakeoverRecommended() {
        ConsultationEntity c = consultations.request(Map.of(
                "subject_cpid", CPID,
                "journey_id", JOURNEY,
                "requesting_service", "MEDICINE",
                "asked_of_service", "SURGERY",
                "question", "Is this abdomen surgical?"));
        return consultations.answer(c.getConsultationId(), Map.of(
                "advice", "Surgical — we should take over",
                "takeover_recommended", true));
    }

    @Test
    void requestingATransferDoesNotMoveOwnership() {
        ConsultationEntity c = answeredConsultationWithTakeoverRecommended();
        assertThat(c.getOwningService()).isEqualTo("MEDICINE");

        CareTransferEntity t = transfers.request(transferBody("consultation_id", c.getConsultationId().toString()));

        assertThat(t.getStatus()).isEqualTo("PENDING");
        assertThat(consultationRepository.findByTenantIdAndConsultationId(TENANT, c.getConsultationId())
                .orElseThrow().getOwningService()).isEqualTo("MEDICINE");
    }

    @Test
    void acceptingWithAcceptingRefMovesOwnershipOnTheLinkedConsultation() {
        ConsultationEntity c = answeredConsultationWithTakeoverRecommended();
        CareTransferEntity t = transfers.request(transferBody("consultation_id", c.getConsultationId().toString()));

        CareTransferEntity accepted = transfers.accept(t.getTransferId(),
                Map.of("accepting_ref", "surg-episode-4471"));

        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.getAcceptingRef()).isEqualTo("surg-episode-4471");
        assertThat(consultationRepository.findByTenantIdAndConsultationId(TENANT, c.getConsultationId())
                .orElseThrow().getOwningService()).isEqualTo("SURGERY");
    }

    @Test
    void acceptingWithoutAcceptingRefIsRefusedAndOwnershipStays() {
        ConsultationEntity c = answeredConsultationWithTakeoverRecommended();
        CareTransferEntity t = transfers.request(transferBody("consultation_id", c.getConsultationId().toString()));

        assertThatThrownBy(() -> transfers.accept(t.getTransferId(), Map.of("accepting_ref", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accepting_ref");

        assertThat(consultationRepository.findByTenantIdAndConsultationId(TENANT, c.getConsultationId())
                .orElseThrow().getOwningService()).isEqualTo("MEDICINE");
        assertThat(transfers.listForSubject(CPID).get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void decliningDoesNotMoveOwnership() {
        ConsultationEntity c = answeredConsultationWithTakeoverRecommended();
        CareTransferEntity t = transfers.request(transferBody("consultation_id", c.getConsultationId().toString()));

        CareTransferEntity declined = transfers.decline(t.getTransferId(), "No theatre slot today");

        assertThat(declined.getStatus()).isEqualTo("DECLINED");
        assertThat(consultationRepository.findByTenantIdAndConsultationId(TENANT, c.getConsultationId())
                .orElseThrow().getOwningService()).isEqualTo("MEDICINE");
    }

    @Test
    void answeringAConsultationStillCannotMoveOwnership() {
        // Guard regression: CareTransferService must not weaken ConsultationService.answer.
        ConsultationEntity c = consultations.request(Map.of(
                "subject_cpid", CPID,
                "journey_id", JOURNEY,
                "requesting_service", "MEDICINE",
                "asked_of_service", "SURGERY",
                "question", "Is this abdomen surgical?"));
        ConsultationEntity answered = consultations.answer(c.getConsultationId(), Map.of(
                "advice", "Surgical — take over",
                "takeover_recommended", true));

        assertThat(answered.isTakeoverRecommended()).isTrue();
        assertThat(answered.getOwningService()).isEqualTo("MEDICINE");
    }

    @Test
    void aSecondPendingTransferToTheSameServiceIsRefused() {
        transfers.request(transferBody());
        assertThatThrownBy(() -> transfers.request(transferBody("reason", "Again")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a pending transfer");
    }

    @Test
    void blankReasonIsRefused() {
        assertThatThrownBy(() -> transfers.request(transferBody("reason", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void transferToSelfIsRefused() {
        assertThatThrownBy(() -> transfers.request(transferBody("to_service", "MEDICINE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");
    }
}
