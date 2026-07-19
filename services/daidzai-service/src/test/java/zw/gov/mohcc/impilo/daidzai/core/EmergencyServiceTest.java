package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.*;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EmergencyServiceTest {

    @Autowired private EmergencyService service;
    @Autowired private EventOutboxRepository outboxRepo;

    @Test
    void anonymousSosRequestPersistsAndEmitsEvent() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "BYSTANDER", null,
                "ANONYMOUS", null, null, "collapsed man ~40", "CARDIAC", "CRITICAL",
                "Man collapsed at market", -17.82, 31.05, "Mbare market", null, "MOBILE");
        assertThat(r.getId()).isNotNull();
        assertThat(r.getSubjectIdentityMode()).isEqualTo("ANONYMOUS");
        assertThat(r.getSubjectCpid()).isNull();
        assertThat(r.getStatus()).isEqualTo("RECEIVED");
        assertThat(outboxRepo.findAll()).anyMatch(e -> e.getEventType().equals("daidzai.request.received"));
    }

    @Test
    void triageClassifiesAndCreatesLinkedIncident() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "CITIZEN", "actor-1",
                "KNOWN", "HID-123", null, null, "MEDICAL", "MODERATE", "fever",
                null, null, "home", null, "WEB");
        EmergencyIncidentEntity inc = service.triageRequestToIncident(tenant, r.getId(), "triager-1");
        assertThat(inc.getTriageCategory()).isEqualTo("YELLOW");
        assertThat(inc.getSeverity()).isEqualTo("MODERATE");
        assertThat(inc.getStatus()).isEqualTo("TRIAGED");
        // request now linked
        EmergencyRequestEntity reread = service.getRequest(tenant, r.getId());
        assertThat(reread.getIncidentId()).isEqualTo(inc.getId());
        assertThat(reread.getStatus()).isEqualTo("LINKED");
    }

    @Test
    void publicAnonymousRequestWithCallbackLandsAwaitingCallback() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "PUBLIC_ANONYMOUS", null,
                "ANONYMOUS", null, null, null, "MEDICAL", "HIGH", "collapsed at bus rank",
                -17.83, 31.05, "Copacabana rank", null, "PUBLIC_WEB", "+263771234567");
        assertThat(r.getStatus()).isEqualTo(EmergencyService.STATUS_AWAITING_CALLBACK);
        assertThat(r.getCallbackNumber()).isEqualTo("+263771234567");
        assertThat(r.getCallbackVerified()).isFalse();
        assertThat(r.getRequesterType()).isEqualTo("PUBLIC_ANONYMOUS");
    }

    @Test
    void triageOnAwaitingCallbackRequestIsRefusedUntilVerified() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "PUBLIC_ANONYMOUS", null,
                "ANONYMOUS", null, null, null, "TRAUMA", "CRITICAL", "RTA",
                null, null, "highway", null, "PUBLIC_WEB", "+263772000111");
        // Dispatch gate holds: no incident until the callback is verified.
        assertThatThrownBy(() -> service.triageRequestToIncident(tenant, r.getId(), "dispatcher-1"))
                .isInstanceOf(CallbackVerificationRequiredException.class)
                .hasMessageContaining("callback verification required");

        // Dispatcher reaches the caller and verifies — the request is released for triage.
        EmergencyRequestEntity verified = service.verifyCallback(tenant, r.getId(), "dispatcher-1");
        assertThat(verified.getCallbackVerified()).isTrue();
        assertThat(verified.getCallbackVerifiedBy()).isEqualTo("dispatcher-1");
        assertThat(verified.getCallbackVerifiedAt()).isNotNull();
        assertThat(verified.getStatus()).isEqualTo("RECEIVED");

        EmergencyIncidentEntity inc = service.triageRequestToIncident(tenant, r.getId(), "dispatcher-1");
        assertThat(inc.getId()).isNotNull();
        assertThat(inc.getStatus()).isEqualTo("TRIAGED");
        assertThat(outboxRepo.findAll())
                .anyMatch(e -> e.getEventType().equals("daidzai.callback.verified"));
    }

    @Test
    void findRequestByReferenceResolvesTenantScopedAndMissesReturnEmpty() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "PUBLIC_ANONYMOUS", null,
                "ANONYMOUS", null, null, null, "MEDICAL", "HIGH", "collapsed",
                null, null, "rank", null, "PUBLIC_WEB", "+263771234567");
        String ref = r.getRequestReference();

        // Resolves for the owning tenant.
        assertThat(service.findRequestByReference(tenant, ref)).isPresent()
                .get().extracting(EmergencyRequestEntity::getId).isEqualTo(r.getId());
        // Tenant isolation: a reference never resolves under another tenant.
        assertThat(service.findRequestByReference(otherTenant, ref)).isEmpty();
        // Unknown / blank references are empty, never an existence oracle.
        assertThat(service.findRequestByReference(tenant, "SOS-DOES-NOT-EXIST")).isEmpty();
        assertThat(service.findRequestByReference(tenant, "   ")).isEmpty();
        assertThat(service.findRequestByReference(tenant, null)).isEmpty();
    }

    @Test
    void authenticatedSosWithoutCallbackKeepsReceivedDefault() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "CITIZEN", "actor-1",
                "KNOWN", "HID-1", null, null, "MEDICAL", "MODERATE", "fever",
                null, null, "home", null, "WEB");
        assertThat(r.getStatus()).isEqualTo("RECEIVED");
        // Existing behaviour unchanged: an authenticated request triages with no gate.
        EmergencyIncidentEntity inc = service.triageRequestToIncident(tenant, r.getId(), "t");
        assertThat(inc.getStatus()).isEqualTo("TRIAGED");
    }

    @Test
    void sensitiveCategoryFlaggedForMasking() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = service.createRequest(tenant, "CITIZEN", "actor-1",
                "KNOWN", "HID-9", null, null, "GBV", "HIGH", "assault",
                null, null, null, null, "WEB");
        assertThat(r.getSensitive()).isTrue();
        EmergencyIncidentEntity inc = service.triageRequestToIncident(tenant, r.getId(), "t");
        assertThat(inc.getSensitive()).isTrue();
    }

    @Test
    void dispatchCreatesMissionTimelineAndTracksStatus() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.escalateToIncident(tenant, "MAJOR_TRAUMA", "HIGH",
                "RTA", null, "UNKNOWN", null, null, null, "highway", "provider-1");
        service.requestDispatch(tenant, inc.getId(), "ambulance please", "dispatcher-1");
        service.recordMissionEvent(tenant, inc.getId(), "RESPONDING", "NHUME-MISSION-1",
                null, "responder-1", "RESPONDER");
        List<MissionEventEntity> timeline = service.missionTimeline(tenant, inc.getId());
        assertThat(timeline).extracting(MissionEventEntity::getStatus)
                .contains("DISPATCH_REQUESTED", "RESPONDING");
        EmergencyIncidentEntity reread = service.getIncident(tenant, inc.getId());
        assertThat(reread.getStatus()).isEqualTo("RESPONDING");
        assertThat(reread.getNhumeMissionRef()).isEqualTo("NHUME-MISSION-1");
    }

    @Test
    void clinicalHandoffLinksPctEncounterWithoutDuplicatingRecord() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.escalateToIncident(tenant, "MEDICAL", "HIGH",
                "x", null, "KNOWN", "HID-5", null, null, null, "actor-1");
        EmergencyIncidentEntity after = service.recordClinicalHandoff(tenant, inc.getId(), "PCT-ENC-77", "responder-1");
        assertThat(after.getPctEncounterRef()).isEqualTo("PCT-ENC-77");
        assertThat(after.getStatus()).isEqualTo("HANDOVER");
    }

    @Test
    void deathOutcomeRoutesToDeathPathwayWithoutDuplicatingRecord() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.escalateToIncident(tenant, "MAJOR_TRAUMA", "CRITICAL",
                "RTA fatality", null, "UNKNOWN", null, null, null, "highway", "responder-1");
        EmergencyIncidentEntity after = service.recordDeathOutcome(tenant, inc.getId(), "IN_TRANSIT", "responder-1");
        assertThat(after.getStatus()).isEqualTo("DECEASED");
        List<MissionEventEntity> timeline = service.missionTimeline(tenant, inc.getId());
        assertThat(timeline).extracting(MissionEventEntity::getStatus).contains("DECEASED");
    }

    @Test
    void resourceRequestRecordsNeedAgainstOwner() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.escalateToIncident(tenant, "HAEMORRHAGE", "CRITICAL",
                "bleed", null, "KNOWN", "HID-2", null, null, null, "a");
        ResourceRequestEntity rr = service.requestResource(tenant, inc.getId(), "BLOOD", "MADI", 2,
                "O-negative", "clinician-1");
        assertThat(rr.getResourceOwner()).isEqualTo("MADI");
        assertThat(rr.getStatus()).isEqualTo("REQUESTED");
        assertThat(service.resources(tenant, inc.getId())).hasSize(1);
    }

    @Test
    void listRequestsReturnsPendingQueueAndExcludesTriagedAndOtherTenants() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        // Fresh tenant → empty pending queue.
        assertThat(service.listRequests(tenant, "RECEIVED")).isEmpty();

        EmergencyRequestEntity pending = service.createRequest(tenant, "BYSTANDER", null,
                "ANONYMOUS", null, null, "adult male RTC", "TRAUMA", "CRITICAL",
                "RTC on highway", -17.83, 31.05, "highway", null, "MOBILE");
        EmergencyRequestEntity toTriage = service.createRequest(tenant, "CITIZEN", "actor-1",
                "KNOWN", "HID-9", null, null, "MEDICAL", "MODERATE", "fever",
                null, null, "home", null, "WEB");

        // Both are RECEIVED and visible on the pending queue.
        assertThat(service.listRequests(tenant, "RECEIVED"))
                .extracting(EmergencyRequestEntity::getId)
                .contains(pending.getId(), toTriage.getId());

        // Triage moves one to LINKED — it drops off the RECEIVED queue.
        service.triageRequestToIncident(tenant, toTriage.getId(), "triager-1");
        assertThat(service.listRequests(tenant, "RECEIVED"))
                .extracting(EmergencyRequestEntity::getId)
                .contains(pending.getId())
                .doesNotContain(toTriage.getId());

        // Tenant isolation: another tenant sees none of these.
        assertThat(service.listRequests(otherTenant, "RECEIVED")).isEmpty();
        // No status filter → all requests for the tenant (both).
        assertThat(service.listRequests(tenant, null))
                .extracting(EmergencyRequestEntity::getId)
                .contains(pending.getId(), toTriage.getId());
    }
}
