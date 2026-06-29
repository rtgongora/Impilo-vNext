package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.domain.CaseClassification;
import zw.gov.mohcc.impilo.rito.domain.CaseLifecycle;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.EventOutboxRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CaseLifecycleTest {

    @Autowired private CaseService caseService;
    @Autowired private EventOutboxRepository outboxRepository;

    private CaseService.NewCase complaint(String severity) {
        return new CaseService.NewCase("COMPLAINT", "Long wait", "Waited 3 hours", severity,
                "WEB_PORTAL", null, false, UUID.randomUUID(), null, null, null,
                "citizen-1", "CITIZEN", "HID-1", false, Map.of("k", "v"));
    }

    @Test
    void client_can_only_read_own_case() {
        UUID tenant = UUID.randomUUID();
        CaseEntity c = caseService.createCase(tenant, complaint("MODERATE")); // reporter citizen-1, subject HID-1

        // Owner (reporter) and subject can read; staff (non-client) reads (gateway role-gated).
        assertThat(caseService.get(tenant, c.getId(), "citizen-1", "CITIZEN").getId()).isEqualTo(c.getId());
        assertThat(caseService.get(tenant, c.getId(), "HID-1", "CITIZEN").getId()).isEqualTo(c.getId());
        assertThat(caseService.get(tenant, c.getId(), "focal-1", "FACILITY_SAFETY_FOCAL").getId()).isEqualTo(c.getId());

        // A different client is forbidden (own-subject binding) — and the sub-reads inherit it.
        assertThatThrownBy(() -> caseService.get(tenant, c.getId(), "other-citizen", "CITIZEN"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> caseService.timeline(tenant, c.getId(), "other-citizen", "CITIZEN"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        // list: a different client sees none of it; the owner sees their case.
        assertThat(caseService.list(tenant, null, null, null, null, "other-citizen", "CITIZEN")).isEmpty();
        assertThat(caseService.list(tenant, null, null, null, null, "citizen-1", "CITIZEN"))
                .extracting(CaseEntity::getId).contains(c.getId());
    }

    @Test
    void createComplaintAssignsReferencePillarAndEmits() {
        UUID tenant = UUID.randomUUID();
        long before = outboxRepository.count();

        CaseEntity c = caseService.createCase(tenant, complaint("MODERATE"));

        assertThat(c.getCaseReference()).startsWith("RITO-C-");
        assertThat(c.getPillar()).isEqualTo(CaseClassification.CLIENT_VOICE);
        assertThat(c.getStatus()).isEqualTo(CaseLifecycle.SUBMITTED);
        assertThat(c.getSensitive()).isFalse();
        // case.submitted + complaint.submitted + notification.requested
        assertThat(outboxRepository.count()).isGreaterThan(before);

        assertThat(caseService.timeline(tenant, c.getId())).isNotEmpty();
    }

    @Test
    void fullLifecycleToClose() {
        UUID tenant = UUID.randomUUID();
        CaseEntity c = caseService.createCase(tenant, complaint("MODERATE"));
        caseService.acknowledge(tenant, c.getId(), "agent-1", "OPERATOR");
        caseService.triage(tenant, c.getId(), "HIGH", "ACCESS_DELAY", "HIGH", "FACILITY_FOCAL", "auto", "focal-1", "OPERATOR");
        caseService.assign(tenant, c.getId(), "focal-1", "OPERATOR", "facility.quality_focal", "mgr-1", "OPERATOR", "owns access issues");
        caseService.resolve(tenant, c.getId(), "Added triage desk", "RESOLVED", "focal-1", "OPERATOR");
        CaseEntity closed = caseService.close(tenant, c.getId(), 4, "mgr-1", "OPERATOR");

        assertThat(closed.getStatus()).isEqualTo(CaseLifecycle.CLOSED);
        assertThat(closed.getSatisfactionScore()).isEqualTo(4);
        assertThat(closed.getResolvedAt()).isNotNull();
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    void aiActorCannotCloseCriticalCase() {
        UUID tenant = UUID.randomUUID();
        CaseService.NewCase incident = new CaseService.NewCase("SAFETY_INCIDENT", "Fall", "Patient fell",
                "CRITICAL", "PROVIDER_WORKSPACE", null, false, UUID.randomUUID(), null, null, null,
                "nurse-1", "PROVIDER", "HID-9", false, Map.of());
        CaseEntity c = caseService.createCase(tenant, incident);
        assertThat(c.getSensitive()).isTrue();
        caseService.acknowledge(tenant, c.getId(), "nurse-1", "PROVIDER");

        assertThatThrownBy(() ->
                caseService.resolve(tenant, c.getId(), "auto-resolved", "RESOLVED", "rules-engine", "AI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("human decision-maker");
    }

    @Test
    void illegalTransitionRejected() {
        UUID tenant = UUID.randomUUID();
        CaseEntity c = caseService.createCase(tenant, complaint("LOW"));
        // SUBMITTED -> CLOSED is not a legal jump
        assertThatThrownBy(() -> caseService.close(tenant, c.getId(), null, "x", "OPERATOR"))
                .isInstanceOf(IllegalStateException.class);
    }
}
