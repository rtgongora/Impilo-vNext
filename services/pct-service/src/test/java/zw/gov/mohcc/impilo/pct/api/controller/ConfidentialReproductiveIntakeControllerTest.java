package zw.gov.mohcc.impilo.pct.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import zw.gov.mohcc.impilo.pct.core.clinical.ConfidentialCarePolicyProvider;
import zw.gov.mohcc.impilo.pct.core.clinical.ConfidentialRecordGuard;
import zw.gov.mohcc.impilo.pct.core.clinical.ConfidentialityStamper;
import zw.gov.mohcc.impilo.pct.core.clinical.DeliveryRecordService;
import zw.gov.mohcc.impilo.pct.core.clinical.FertilityEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.PostnatalContactService;
import zw.gov.mohcc.impilo.pct.core.clinical.PreconceptionService;
import zw.gov.mohcc.impilo.pct.core.clinical.PregnancyEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveIntentionService;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeliveryRecordRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FertilityEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PostnatalContactRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PreconceptionPlanRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyDatingRevisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReproductiveIntentionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two owed intake surfaces, and the failure modes each was shaped by.
 *
 * <p><b>Requests are deserialised from the literal JSON a client sends</b>, not built as records in
 * the test. A snake_case request record against a camelCase client is a silent 400 indistinguishable
 * from a validation failure, and a test that constructs the record directly cannot see it — the wire
 * is the thing under test here, not the constructor.
 */
class ConfidentialReproductiveIntakeControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT = "CPID-WOMAN-1";

    private static final String[] LANE_MARKERS =
            {"confidential", "safeguarding", "protected-disclosure"};

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private PregnancyEpisodeRepository episodes;
    private PostnatalContactRepository contacts;
    private ReproductiveIntentionRepository intentions;
    private PreconceptionPlanRepository preconceptionPlans;
    private FertilityEpisodeRepository fertilityEpisodes;
    private DeliveryRecordRepository deliveries;
    private ConfidentialReproductiveIntakeController controller;

    @BeforeEach
    void setUp() {
        episodes = mock(PregnancyEpisodeRepository.class);
        when(episodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(episodes.findOngoing(any(), any())).thenReturn(Optional.empty());
        when(episodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());

        PregnancyDatingRevisionRepository revisions = mock(PregnancyDatingRevisionRepository.class);
        when(revisions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contacts = mock(PostnatalContactRepository.class);
        when(contacts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contacts.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());

        intentions = mock(ReproductiveIntentionRepository.class);
        when(intentions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(intentions.findCurrent(any(), any())).thenReturn(Optional.empty());
        when(intentions.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
        when(intentions.history(any(), any())).thenReturn(java.util.List.of());

        preconceptionPlans = mock(PreconceptionPlanRepository.class);
        when(preconceptionPlans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preconceptionPlans.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());

        fertilityEpisodes = mock(FertilityEpisodeRepository.class);
        when(fertilityEpisodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fertilityEpisodes.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());

        deliveries = mock(DeliveryRecordRepository.class);
        when(deliveries.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveries.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
        when(deliveries.findForPregnancy(any(), any())).thenReturn(Optional.empty());

        controller = new ConfidentialReproductiveIntakeController(
                new PregnancyEpisodeService(episodes, revisions, inertPolicy(),
                        new ConfidentialRecordGuard()),
                new PostnatalContactService(contacts, inertPolicy(), new ConfidentialRecordGuard()),
                new ReproductiveIntentionService(intentions),
                new PreconceptionService(preconceptionPlans),
                new FertilityEpisodeService(fertilityEpisodes),
                new DeliveryRecordService(deliveries));

        TrustContextHolder.set(new TrustContext(TENANT, "chw-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    // ── pregnancy booking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the lane markers are on the class mapping, so the PDP will classify these writes")
    void mountedOnAConfidentialLane() {
        RequestMapping mapping = ConfidentialReproductiveIntakeController.class
                .getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        String path = mapping.value()[0].toLowerCase();
        // V048 seeds confidential-lane-write-self / -write-clinician / -write-nurse against
        // path_contains "/confidential/". A reproductive write mounted elsewhere is not merely
        // unclassified — it is outside the entitlement that was written for it.
        assertThat(Arrays.stream(LANE_MARKERS).anyMatch(path::contains))
                .as("path '%s' carries no lane marker", path).isTrue();
    }

    @Test
    @DisplayName("a booking from the literal camelCase JSON a client sends returns 201 and its dating")
    void bookingFromTheWire() throws Exception {
        var request = json.readValue("""
                {
                  "subjectCpid": "CPID-WOMAN-1",
                  "datingBases": [
                    {"method": "LMP", "lastMenstrualPeriod": "2026-01-05",
                     "lastMenstrualPeriodCertain": true}
                  ],
                  "recordedOn": "2026-03-01",
                  "confirmationBasis": "URINE_HCG",
                  "clientOfflineId": "offline-booking-1",
                  "recordedBy": "midwife-1"
                }
                """, ConfidentialReproductiveIntakeController.OpenPregnancyRequest.class);

        var response = controller.openPregnancy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = data(response);
        assertThat(data.get("subject_cpid")).isEqualTo(SUBJECT);
        assertThat(data.get("estimated_delivery_date")).isNotNull();
        assertThat(data.get("replayed")).isEqualTo(false);
        // snake_case out, camelCase in — both halves proven on one round trip.
        assertThat(data).containsKey("pregnancy_episode_id").doesNotContainKey("pregnancyEpisodeId");
    }

    @Test
    @DisplayName("AN ALREADY-BOOKED PREGNANCY IS 409 WITH THE EXISTING ID AND A TASK, NEVER 500")
    void duplicateBookingIsAConflictWithSomethingToActOn() throws Exception {
        PregnancyEpisodeEntity existing = new PregnancyEpisodeEntity();
        UUID existingId = UUID.randomUUID();
        existing.setPregnancyEpisodeId(existingId);
        existing.setTenantId(TENANT);
        existing.setSubjectCpid(SUBJECT);
        existing.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        when(episodes.findOngoing(TENANT, SUBJECT)).thenReturn(Optional.of(existing));

        var response = controller.openPregnancy(booking("offline-booking-2"));

        // A 500 here silently loses a booking captured in a clinic with no network, which is the exact
        // case the offline mechanism exists to serve.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> data = data(response);
        assertThat(data.get("code")).isEqualTo("PREGNANCY_ALREADY_BOOKED");
        // A bare 409 leaves the client with nothing to do about it. The id is what makes it actionable.
        assertThat(data.get("existing_pregnancy_episode_id")).isEqualTo(existingId.toString());
        assertThat((String) data.get("reconciliation_task")).isNotBlank();
    }

    @Test
    @DisplayName("a replayed offline booking is 200 with the existing episode, not a second pregnancy")
    void replayedBookingIsNotASecondPregnancy() throws Exception {
        PregnancyEpisodeEntity already = new PregnancyEpisodeEntity();
        already.setPregnancyEpisodeId(UUID.randomUUID());
        already.setTenantId(TENANT);
        already.setSubjectCpid(SUBJECT);
        already.setClientOfflineId("offline-booking-3");
        already.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        when(episodes.findByTenantIdAndClientOfflineId(TENANT, "offline-booking-3"))
                .thenReturn(Optional.of(already));

        var response = controller.openPregnancy(booking("offline-booking-3"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Reported by the service, not inferred here: a replay and a new booking are identical on the
        // row, so a controller guessing from a timestamp would be right most of the time.
        assertThat(data(response).get("replayed")).isEqualTo(true);
    }

    @Test
    @DisplayName("a booking with no usable dating basis is refused 422, never defaulted to today")
    void undatableBookingIsRefused() throws Exception {
        var request = json.readValue("""
                {"subjectCpid": "CPID-WOMAN-1", "datingBases": [], "recordedBy": "midwife-1"}
                """, ConfidentialReproductiveIntakeController.OpenPregnancyRequest.class);

        var response = controller.openPregnancy(request);

        // Without a start date every gestation-scoped rule downstream silently fails to apply rather
        // than reporting that it could not.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(data(response).get("code")).isEqualTo("PREGNANCY_UNDATABLE");
    }

    @Test
    @DisplayName("an unrecognised dating method is dropped rather than guessed, and the request refused")
    void unknownDatingMethodIsNotGuessed() throws Exception {
        var request = json.readValue("""
                {"subjectCpid": "CPID-WOMAN-1",
                 "datingBases": [{"method": "FUNDAL_HEIGHT_EYEBALL", "observedOn": "2026-03-01"}],
                 "recordedBy": "midwife-1"}
                """, ConfidentialReproductiveIntakeController.OpenPregnancyRequest.class);

        var response = controller.openPregnancy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("an uncertain LMP is not promoted to certain by a missing flag")
    void nullLmpCertaintyIsNotCertainty() throws Exception {
        var request = json.readValue("""
                {"subjectCpid": "CPID-WOMAN-1",
                 "datingBases": [{"method": "LMP", "lastMenstrualPeriod": "2026-01-05"}],
                 "recordedOn": "2026-03-01", "recordedBy": "midwife-1"}
                """, ConfidentialReproductiveIntakeController.OpenPregnancyRequest.class);

        var response = controller.openPregnancy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Recall a clinician did not vouch for must not carry the confidence a scan would.
        assertThat(data(response).get("dating_confidence")).isNotEqualTo("HIGH");
    }

    // ── postnatal contact ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a HOME visit is recorded as HOME, and an unscreened contact stays unscreened")
    void homeVisitKeepsItsSettingAndItsSilence() throws Exception {
        var request = json.readValue("""
                {
                  "motherCpid": "CPID-WOMAN-1",
                  "contactTiming": "DAY_3",
                  "contactSetting": "HOME",
                  "contactedAt": "2026-03-04T09:15:00Z",
                  "breastfeedingStatus": "NOT_ASSESSED",
                  "clientOfflineId": "offline-pnc-1",
                  "recordedBy": "chw-1"
                }
                """, ConfidentialReproductiveIntakeController.PostnatalContactRequest.class);

        var response = controller.recordPostnatalContact(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = data(response);
        // Not a facility visit with a flag. Defaulting to FACILITY would relabel every CHW contact as
        // a clinic attendance — the contact the PNC schedule is worst at capturing.
        assertThat(data.get("contact_setting")).isEqualTo("HOME");
        // No screen was asserted, so danger signs stay null. A false here would be the reassuring
        // all-clear V436 chk_pnc_screening_gate exists to refuse.
        assertThat(data.get("danger_signs_present")).isNull();
        assertThat(data.get("screening_complete")).isEqualTo(false);
        // NOT_ASSESSED is carried, not collapsed: "we did not look" is not "she is not breastfeeding".
        assertThat(data.get("breastfeeding_status")).isEqualTo("NOT_ASSESSED");
    }

    @Test
    @DisplayName("a replayed CHW packet returns the existing contact, not a duplicate visit")
    void replayedContactIsNotADuplicateVisit() throws Exception {
        PostnatalContactEntity already = new PostnatalContactEntity();
        already.setPostnatalContactId(UUID.randomUUID());
        already.setTenantId(TENANT);
        already.setMotherCpid(SUBJECT);
        already.setContactSetting(PostnatalContactEntity.SETTING_HOME);
        already.setClientOfflineId("offline-pnc-2");
        when(contacts.findByTenantIdAndClientOfflineId(TENANT, "offline-pnc-2"))
                .thenReturn(Optional.of(already));

        var response = controller.recordPostnatalContact(contactJson("offline-pnc-2", "HOME", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = data(response);
        assertThat(data.get("replayed")).isEqualTo(true);
        assertThat(data.get("postnatal_contact_id"))
                .isEqualTo(already.getPostnatalContactId().toString());
    }

    @Test
    @DisplayName("a contact with no setting is refused rather than defaulted to FACILITY")
    void missingSettingIsRefused() throws Exception {
        var response = controller.recordPostnatalContact(
                contactJson("offline-pnc-3", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(data(response).get("code")).isEqualTo("PNC_SETTING_REQUIRED");
    }

    @Test
    @DisplayName("a referral with no reason is refused: a woman sent onward with nothing said about why")
    void referralWithoutAReasonIsRefused() throws Exception {
        var request = json.readValue("""
                {"motherCpid": "CPID-WOMAN-1", "contactSetting": "COMMUNITY",
                 "referralMade": true, "recordedBy": "chw-1"}
                """, ConfidentialReproductiveIntakeController.PostnatalContactRequest.class);

        var response = controller.recordPostnatalContact(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(data(response).get("code")).isEqualTo("PNC_REFERRAL_REASON_REQUIRED");
    }

    @Test
    @DisplayName("a contact carrying contraception content is stamped SRH; a routine one is not")
    void onlyContraceptionContentIsStamped() throws Exception {
        var routine = controller.recordPostnatalContact(
                contactJson("offline-pnc-4", "HOME", null));
        assertThat(data(routine).get("confidentiality_category"))
                .as("over-marking would put half the postnatal register behind a grant nobody holds")
                .isNull();

        var withFp = controller.recordPostnatalContact(
                contactJson("offline-pnc-5", "HOME", true));
        assertThat(data(withFp).get("confidentiality_category")).isEqualTo("SEXUAL_REPRODUCTIVE_HEALTH");
        assertThat(data(withFp).get("sensitivity_class"))
                .as("SHADOW until the flip, or the CHW who recorded it could not read it back")
                .isEqualTo("FULL_CLINICAL");
    }

    // ── W14-B: intention and delivery ─────────────────────────────────────────

    @Test
    @DisplayName("recording reproductive intention from camelCase JSON returns 201 snake_case")
    void intentionFromTheWire() throws Exception {
        var request = json.readValue("""
                {"subjectCpid": "CPID-WOMAN-1", "intention": "WANTS_PREGNANCY_LATER",
                 "timeframeMonths": 24, "recordedBy": "nurse-1"}
                """, ConfidentialReproductiveIntakeController.RecordIntentionRequest.class);

        var response = controller.recordIntention(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).get("intention")).isEqualTo("WANTS_PREGNANCY_LATER");
        assertThat(data(response)).containsKey("intention_id").doesNotContainKey("intentionId");
    }

    @Test
    @DisplayName("a duplicate delivery for the same pregnancy is 409 with the existing id, never 500")
    void duplicateDeliveryIsAConflict() throws Exception {
        UUID pregnancyId = UUID.randomUUID();
        DeliveryRecordEntity existing = new DeliveryRecordEntity();
        existing.setDeliveryRecordId(UUID.randomUUID());
        existing.setDeliveredAt(OffsetDateTime.now().minusDays(1));
        when(deliveries.findForPregnancy(TENANT, pregnancyId)).thenReturn(Optional.of(existing));

        var request = json.readValue("""
                {"motherCpid": "CPID-WOMAN-1", "pregnancyEpisodeId": "%s",
                 "deliveredAt": "2026-03-01T08:00:00Z", "deliveryMode": "SPONTANEOUS_VAGINAL",
                 "recordedBy": "midwife-1"}
                """.formatted(pregnancyId),
                ConfidentialReproductiveIntakeController.RecordDeliveryRequest.class);

        var response = controller.recordDelivery(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(data(response).get("code")).isEqualTo("DELIVERY_ALREADY_RECORDED");
        assertThat(data(response).get("existing_delivery_record_id"))
                .isEqualTo(existing.getDeliveryRecordId().toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConfidentialReproductiveIntakeController.OpenPregnancyRequest booking(String offlineId)
            throws Exception {
        return json.readValue("""
                {"subjectCpid": "CPID-WOMAN-1",
                 "datingBases": [{"method": "LMP", "lastMenstrualPeriod": "2026-01-05",
                                  "lastMenstrualPeriodCertain": true}],
                 "recordedOn": "2026-03-01", "clientOfflineId": "%s", "recordedBy": "midwife-1"}
                """.formatted(offlineId),
                ConfidentialReproductiveIntakeController.OpenPregnancyRequest.class);
    }

    private ConfidentialReproductiveIntakeController.PostnatalContactRequest contactJson(
            String offlineId, String setting, Boolean contraceptionDiscussed) throws Exception {
        return json.readValue("""
                {"motherCpid": "CPID-WOMAN-1", "contactTiming": "DAY_3",
                 %s
                 "contactedAt": "2026-03-04T09:15:00Z",
                 %s
                 "clientOfflineId": "%s", "recordedBy": "chw-1"}
                """.formatted(
                        setting == null ? "" : "\"contactSetting\": \"" + setting + "\",",
                        contraceptionDiscussed == null ? ""
                                : "\"contraceptionDiscussed\": " + contraceptionDiscussed + ",",
                        offlineId),
                ConfidentialReproductiveIntakeController.PostnatalContactRequest.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    /** CKP unreachable: the category is still stamped, the class stays FULL_CLINICAL. */
    private static ConfidentialCarePolicyProvider inertPolicy() {
        ConfidentialCarePolicyProvider p = mock(ConfidentialCarePolicyProvider.class);
        when(p.policyAsOf(any(), any())).thenReturn(null);
        when(p.subjectAt(any(), any(), any(), any()))
                .thenReturn(new ConfidentialityStamper.SubjectContext(null, false));
        when(p.classStampingEnabled()).thenReturn(false);
        return p;
    }
}
