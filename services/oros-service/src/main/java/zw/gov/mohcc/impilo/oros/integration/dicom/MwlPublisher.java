package zw.gov.mohcc.impilo.oros.integration.dicom;

import zw.gov.mohcc.impilo.oros.domain.MwlMode;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

/**
 * Strategy for publishing a DICOM Modality Worklist item for an imaging order. Implementations
 * declare the {@link MwlMode} they serve; the router dispatches to the configured one.
 */
public interface MwlPublisher {

    MwlMode mode();

    /** Publish (best-effort) an MWL item for the order's scheduled procedure step. */
    void publish(OrderEntity order, String modality);
}
