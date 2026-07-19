package zw.gov.mohcc.impilo.vashandi.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PJ4 (W5): APPROVED FACILITY_ACCESS materialises as an active assignment with
 * engagement + validity; missing profile skips (no profile invented);
 * duplicates are never created; rejections and other request types do nothing.
 */
@ExtendWith(MockitoExtension.class)
class FacilityAccessApprovedConsumerTest {

    @Mock private WorkforceProfileRepository profileRepository;
    @Mock private WorkforceAssignmentRepository assignmentRepository;

    private FacilityAccessApprovedConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new FacilityAccessApprovedConsumer(
                new ObjectMapper(), profileRepository, assignmentRepository);
        lenient().when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String approvedEvent(String requestType, String status) {
        return String.format(
                "{\"event_type\":\"impilo.varapi.provider_access_request.decided.v1\",\"tenant_id\":\"%s\","
                        + "\"payload\":{\"publicId\":\"PAR-1\",\"requestType\":\"%s\",\"status\":\"%s\","
                        + "\"decidedBy\":\"fa-1\",\"applicantHealthId\":\"%s\",\"facilityId\":\"%s\","
                        + "\"engagementType\":\"LOCUM\",\"accessValidFrom\":\"2026-07-20\","
                        + "\"accessValidTo\":\"2026-08-20\"}}",
                tenantId, requestType, status, healthId, facilityId);
    }

    private void stubProfile() {
        WorkforceProfileEntity profile = mock(WorkforceProfileEntity.class);
        lenient().when(profile.getId()).thenReturn(profileId);
        lenient().when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.of(profile));
    }

    @Test
    void approvedFacilityAccessMaterialisesAssignment() {
        stubProfile();
        when(assignmentRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "active"))
                .thenReturn(List.of());

        consumer.onAccessRequestEvent(approvedEvent("FACILITY_ACCESS", "APPROVED"));

        ArgumentCaptor<WorkforceAssignmentEntity> captor =
                ArgumentCaptor.forClass(WorkforceAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        WorkforceAssignmentEntity a = captor.getValue();
        assertThat(a.getWorkforceProfileId()).isEqualTo(profileId);
        assertThat(a.getFacilityId()).isEqualTo(facilityId);
        assertThat(a.getEngagementType()).isEqualTo("LOCUM");
        assertThat(a.getStatus()).isEqualTo("active");
        assertThat(a.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(a.getSourceAuthority()).isEqualTo("VARAPI_ACCESS_REQUEST:PAR-1");
    }

    @Test
    void missingProfileSkipsWithoutInventingOne() {
        when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.empty());

        consumer.onAccessRequestEvent(approvedEvent("FACILITY_ACCESS", "APPROVED"));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void duplicateActiveEngagementIsNotRecreated() {
        stubProfile();
        WorkforceAssignmentEntity existing = new WorkforceAssignmentEntity();
        existing.setWorkforceProfileId(profileId);
        existing.setEngagementType("LOCUM");
        when(assignmentRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "active"))
                .thenReturn(List.of(existing));

        consumer.onAccessRequestEvent(approvedEvent("FACILITY_ACCESS", "APPROVED"));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void rejectionsAndOtherTypesDoNothing() {
        consumer.onAccessRequestEvent(approvedEvent("FACILITY_ACCESS", "REJECTED"));
        consumer.onAccessRequestEvent(approvedEvent("NEW_PROVIDER", "APPROVED"));
        verify(assignmentRepository, never()).save(any());
        verify(profileRepository, never()).findByTenantIdAndHealthId(any(), any());
    }
}
