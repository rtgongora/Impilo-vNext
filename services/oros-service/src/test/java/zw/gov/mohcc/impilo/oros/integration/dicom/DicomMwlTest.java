package zw.gov.mohcc.impilo.oros.integration.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.domain.MwlMode;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DICOM MWL-outbound: dataset mapping (dcm4che, no network), REST DICOM-JSON
 * publication, the UPS dataset for DIMSE, and mode routing (OFF = honest NOT_LIVE default).
 */
class DicomMwlTest {

    private OrderEntity order() {
        OrderEntity o = new OrderEntity();
        o.setOrderId("ORD-1");
        o.setAccessionNumber("ACC-2026-AB-1");
        o.setPatientCpid("CPID-9");
        o.setScheduledAt(OffsetDateTime.parse("2026-07-01T09:30:00Z"));
        o.setStudyUid("1.2.840.113619.2.55");
        return o;
    }

    @Test
    @DisplayName("MwlDatasetBuilder maps the order into an MWL scheduled-procedure-step dataset")
    void buildsMwlDataset() {
        Attributes a = new MwlDatasetBuilder().build(order(), "CT", "IMPILO");

        assertThat(a.getString(Tag.AccessionNumber)).isEqualTo("ACC-2026-AB-1");
        assertThat(a.getString(Tag.PatientID)).isEqualTo("CPID-9");
        assertThat(a.getString(Tag.RequestedProcedureID)).isEqualTo("ORD-1");
        assertThat(a.getString(Tag.StudyInstanceUID)).isEqualTo("1.2.840.113619.2.55");
        Attributes sps = a.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        assertThat(sps).isNotNull();
        assertThat(sps.getString(Tag.Modality)).isEqualTo("CT");
        assertThat(sps.getString(Tag.ScheduledProcedureStepStartDate)).isEqualTo("20260701");
    }

    @Test
    @DisplayName("DimseMwlPublisher.buildUpsDataset adds the UPS-required attributes")
    void buildsUpsDataset() {
        DimseMwlPublisher p = new DimseMwlPublisher(new MwlDatasetBuilder(),
                "IMPILO_OROS", "DCM4CHEE", "localhost", 11112, "IMPILO");
        Attributes ds = p.buildUpsDataset(order(), "CT");

        assertThat(ds.getString(Tag.SOPClassUID)).isEqualTo(UID.UnifiedProcedureStepPush);
        assertThat(ds.getString(Tag.ProcedureStepState)).isEqualTo("SCHEDULED");
        assertThat(ds.getString(Tag.InputReadinessState)).isEqualTo("READY");
        assertThat(p.mode()).isEqualTo(MwlMode.DIMSE);
    }

    @Test
    @DisplayName("RestMwlPublisher POSTs DICOM JSON to the archive MWL endpoint")
    @SuppressWarnings("unchecked")
    void restPublishesDicomJson() {
        RestTemplate rt = mock(RestTemplate.class);
        RestMwlPublisher p = new RestMwlPublisher(rt, "http://arc:8088", "/rs/mwlitems", "IMPILO");

        p.publish(order(), "CT");

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(rt).postForEntity(eq("http://arc:8088/rs/mwlitems"), captor.capture(), eq(String.class));
        String body = captor.getValue().getBody().toString();
        assertThat(body).contains("00080050").contains("ACC-2026-AB-1").contains("CPID-9").contains("CT");
        assertThat(p.mode()).isEqualTo(MwlMode.REST);
    }

    @Test
    @DisplayName("RestMwlPublisher with no base URL is a no-op")
    void restNoBaseUrlNoOp() {
        RestTemplate rt = mock(RestTemplate.class);
        new RestMwlPublisher(rt, "  ", "/rs/mwlitems", "IMPILO").publish(order(), "CT");
        verifyNoInteractions(rt);
    }

    @Test
    @DisplayName("router OFF (default) publishes nothing; REST routes to the REST publisher")
    void routerDispatch() {
        MwlPublisher rest = mock(MwlPublisher.class);
        when(rest.mode()).thenReturn(MwlMode.REST);

        new MwlPublisherRouter(List.of(rest), "OFF").publish(order(), "CT");
        verify(rest, never()).publish(any(), any());

        new MwlPublisherRouter(List.of(rest), "REST").publish(order(), "CT");
        verify(rest).publish(any(), eq("CT"));
    }
}
