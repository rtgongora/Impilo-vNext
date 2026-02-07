package zw.gov.mohcc.impilo.tshepo.offline.exception;

import java.util.UUID;

/**
 * Thrown when a requested capability token is not found.
 */
public class CapabilityTokenNotFoundException extends OfflineServiceException {

    public CapabilityTokenNotFoundException(UUID tokenId) {
        super("TOKEN_NOT_FOUND", "Capability token not found: " + tokenId);
    }
}
