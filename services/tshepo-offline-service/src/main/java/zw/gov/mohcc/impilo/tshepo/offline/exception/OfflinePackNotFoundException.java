package zw.gov.mohcc.impilo.tshepo.offline.exception;

import java.util.UUID;

/**
 * Thrown when a requested offline pack is not found.
 */
public class OfflinePackNotFoundException extends OfflineServiceException {

    public OfflinePackNotFoundException(UUID packId) {
        super("PACK_NOT_FOUND", "Offline pack not found: " + packId);
    }

    public OfflinePackNotFoundException(UUID facilityId, boolean forFacility) {
        super("PACK_NOT_FOUND", "No offline pack found for facility: " + facilityId);
    }
}
