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
        assertThat(r.getSubjectHealthId()).isNull();
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
}
