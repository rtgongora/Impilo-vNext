package zw.gov.mohcc.impilo.oros.integration.dicom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.domain.MwlMode;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MWL-outbound via DICOMweb/UPS-RS REST — posts a DICOM-JSON (PS3.18) MWL/UPS item to an archive
 * (dcm4chee-arc or Orthanc). The archive then serves modalities via MWL C-FIND. Best-effort; the
 * router only invokes this when {@code oros.integration.dicom.mwl.mode=REST}.
 */
@Component
public class RestMwlPublisher implements MwlPublisher {

    private static final Logger log = LoggerFactory.getLogger(RestMwlPublisher.class);
    private static final DateTimeFormatter DA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM = DateTimeFormatter.ofPattern("HHmmss");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String path;
    private final String scheduledStationAet;

    public RestMwlPublisher(RestTemplate restTemplate,
                            @Value("${oros.integration.dicom.mwl.rest.base-url:http://localhost:8088}") String baseUrl,
                            @Value("${oros.integration.dicom.mwl.rest.path:/dcm4chee-arc/aets/DCM4CHEE/rs/mwlitems}") String path,
                            @Value("${oros.integration.dicom.mwl.scheduled-station-aet:IMPILO}") String scheduledStationAet) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.path = path;
        this.scheduledStationAet = scheduledStationAet;
    }

    @Override
    public MwlMode mode() {
        return MwlMode.REST;
    }

    @Override
    public void publish(OrderEntity order, String modality) {
        if (baseUrl.isBlank()) {
            log.warn("MWL REST publish skipped — no base URL configured (orderId={})", order.getOrderId());
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/dicom+json"));
            restTemplate.postForEntity(baseUrl + path,
                    new HttpEntity<>(dicomJson(order, modality), headers), String.class);
            log.info("MWL item published via REST for orderId={} to {}{}", order.getOrderId(), baseUrl, path);
        } catch (Exception e) {
            log.warn("MWL REST publish failed for orderId={}: {}", order.getOrderId(), e.getMessage());
        }
    }

    /** Minimal valid DICOM JSON (PS3.18) for the MWL scheduled procedure step. */
    private List<Map<String, Object>> dicomJson(OrderEntity order, String modality) {
        OffsetDateTime when = order.getScheduledAt() != null ? order.getScheduledAt() : OffsetDateTime.now();
        Map<String, Object> sps = new LinkedHashMap<>();
        sps.put("00080060", vr("CS", modality != null ? modality : "OT"));
        sps.put("00400002", vr("DA", when.format(DA)));
        sps.put("00400003", vr("TM", when.format(TM)));
        sps.put("00400009", vr("SH", order.getOrderId()));
        sps.put("00400001", vr("AE", scheduledStationAet));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("00080050", vr("SH", order.getAccessionNumber() != null ? order.getAccessionNumber() : order.getOrderId()));
        item.put("00100020", vr("LO", order.getPatientCpid()));
        item.put("00401001", vr("SH", order.getOrderId()));
        if (order.getStudyUid() != null) {
            item.put("0020000D", vr("UI", order.getStudyUid()));
        }
        item.put("00400100", Map.of("vr", "SQ", "Value", List.of(sps)));
        return List.of(item);
    }

    private static Map<String, Object> vr(String vr, String value) {
        return Map.of("vr", vr, "Value", List.of(value));
    }
}
