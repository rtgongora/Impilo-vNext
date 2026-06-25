package zw.gov.mohcc.impilo.oros.integration.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.domain.MwlMode;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

/**
 * MWL-outbound via DICOM DIMSE — publishes a Unified Procedure Step (UPS Push, N-CREATE) to an
 * archive/SCP using dcm4che. The order's scheduled procedure step becomes a worklist item the
 * modality can query. Best-effort; the router only invokes this when
 * {@code oros.integration.dicom.mwl.mode=DIMSE}.
 */
@Component
public class DimseMwlPublisher implements MwlPublisher {

    private static final Logger log = LoggerFactory.getLogger(DimseMwlPublisher.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MwlDatasetBuilder datasetBuilder;
    private final String callingAet;
    private final String calledAet;
    private final String host;
    private final int port;
    private final String scheduledStationAet;

    public DimseMwlPublisher(MwlDatasetBuilder datasetBuilder,
                             @Value("${oros.integration.dicom.mwl.dimse.calling-aet:IMPILO_OROS}") String callingAet,
                             @Value("${oros.integration.dicom.mwl.dimse.called-aet:DCM4CHEE}") String calledAet,
                             @Value("${oros.integration.dicom.mwl.dimse.host:localhost}") String host,
                             @Value("${oros.integration.dicom.mwl.dimse.port:11112}") int port,
                             @Value("${oros.integration.dicom.mwl.scheduled-station-aet:IMPILO}") String scheduledStationAet) {
        this.datasetBuilder = datasetBuilder;
        this.callingAet = callingAet;
        this.calledAet = calledAet;
        this.host = host;
        this.port = port;
        this.scheduledStationAet = scheduledStationAet;
    }

    @Override
    public MwlMode mode() {
        return MwlMode.DIMSE;
    }

    /** Build the UPS dataset (always) and attempt the N-CREATE association (best-effort). */
    public Attributes buildUpsDataset(OrderEntity order, String modality) {
        Attributes ds = datasetBuilder.build(order, modality, scheduledStationAet);
        OffsetDateTime when = order.getScheduledAt() != null ? order.getScheduledAt() : OffsetDateTime.now();
        // UPS-required attributes (PS3.4 CC).
        ds.setString(Tag.SOPClassUID, VR.UI, UID.UnifiedProcedureStepPush);
        ds.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DT, when.format(DT));
        ds.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        ds.setString(Tag.InputReadinessState, VR.CS, "READY");
        ds.setString(Tag.WorklistLabel, VR.LO, "IMPILO-DIAGNOSTICS");
        return ds;
    }

    @Override
    public void publish(OrderEntity order, String modality) {
        Attributes ds = buildUpsDataset(order, modality);
        Device device = new Device("oros-mwl-scu");
        try {
            Connection local = new Connection();
            ApplicationEntity ae = new ApplicationEntity(callingAet);
            ae.setAssociationInitiator(true);
            ae.addConnection(local);
            device.addConnection(local);
            device.addApplicationEntity(ae);
            device.setExecutor(Executors.newSingleThreadExecutor());
            device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());

            Connection remote = new Connection();
            remote.setHostname(host);
            remote.setPort(port);

            AAssociateRQ rq = new AAssociateRQ();
            rq.setCalledAET(calledAet);
            rq.addPresentationContext(new PresentationContext(
                    1, UID.UnifiedProcedureStepPush, UID.ImplicitVRLittleEndian));

            Association as = ae.connect(remote, rq);
            try {
                DimseRSP rsp = as.ncreate(UID.UnifiedProcedureStepPush, UIDUtils.createUID(), ds, null);
                rsp.next();
                log.info("MWL UPS N-CREATE sent via DIMSE for orderId={} to {}@{}:{}",
                        order.getOrderId(), calledAet, host, port);
            } finally {
                as.release();
            }
        } catch (Exception e) {
            log.warn("MWL DIMSE publish failed for orderId={} to {}@{}:{}: {}",
                    order.getOrderId(), calledAet, host, port, e.getMessage());
        } finally {
            device.unbindConnections();
        }
    }
}
